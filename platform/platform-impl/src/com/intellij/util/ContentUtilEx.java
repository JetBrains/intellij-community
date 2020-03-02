// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.content.impl.TabbedContentImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public class ContentUtilEx extends ContentsUtil {

  public static void addTabbedContent(@NotNull ContentManager manager,
                                      @NotNull JComponent contentComponent,
                                      @NotNull String groupPrefix,
                                      @NotNull String tabName,
                                      boolean select) {
    addTabbedContent(manager, contentComponent, groupPrefix, tabName, select, null);
  }

  public static void addTabbedContent(@NotNull ContentManager manager,
                                      @NotNull JComponent contentComponent,
                                      @NotNull String groupPrefix,
                                      @NotNull String tabName,
                                      boolean select,
                                      @Nullable Disposable childDisposable) {
    addTabbedContentImpl(manager, contentComponent, groupPrefix, tabName, select, childDisposable);
  }

  public static void addTabbedContent(@NotNull ContentManager manager,
                                      @NotNull JComponent contentComponent,
                                      @NotNull @NonNls String groupPrefix,
                                      @NotNull @NonNls String tabName,
                                      @NotNull Supplier<@Nls String> groupDisplayName,
                                      @NotNull Supplier<@Nls String> tabDisplayName,
                                      boolean select,
                                      @Nullable Disposable childDisposable) {
    contentComponent.putClientProperty(TAB_DISPLAY_PREFIX, groupDisplayName);
    contentComponent.putClientProperty(TAB_DISPLAY_NAME, tabDisplayName);
    Disposable disposable = ObjectUtils.chooseNotNull(childDisposable, getDisposable(contentComponent));
    if (disposable != null) {
      Disposer.register(disposable, () -> {
        contentComponent.putClientProperty(TAB_DISPLAY_NAME, null);
        contentComponent.putClientProperty(TAB_DISPLAY_PREFIX, null);
      });
    }

    addTabbedContentImpl(manager, contentComponent, groupPrefix, tabName, select, childDisposable);
  }

  private static void addTabbedContentImpl(@NotNull ContentManager manager,
                                           @NotNull JComponent contentComponent,
                                           @NonNls @NotNull String groupPrefix,
                                           @NonNls @NotNull String tabName,
                                           boolean select,
                                           @Nullable Disposable childDisposable) {
    if (isSplitMode(groupPrefix)) {
      String fullName = getFullName(groupPrefix, tabName);
      String displayName = ObjectUtils.chooseNotNull(getDisplayName(contentComponent, true), fullName);

      Content content = ContentFactory.SERVICE.getInstance().createContent(contentComponent, displayName, true);
      content.setTabName(fullName);
      content.putUserData(Content.TABBED_CONTENT_KEY, Boolean.TRUE);
      content.putUserData(Content.TAB_GROUP_NAME_KEY, groupPrefix);

      for (Content c : manager.getContents()) {
        if (c.getComponent() == contentComponent) {
          if (select) {
            manager.setSelectedContent(c);
          }
          return;
        }
      }
      addContent(manager, content, select);

      registerDisposable(content, childDisposable, contentComponent);

      return;
    }

    TabbedContent tabbedContent = findTabbedContent(manager, groupPrefix);

    if (tabbedContent == null) {
      final Disposable disposable = Disposer.newDisposable();
      tabbedContent = new TabbedContentImpl(contentComponent, tabName, true, groupPrefix);
      ContentsUtil.addContent(manager, tabbedContent, select);
      Disposer.register(tabbedContent, disposable);
    }
    else {
      for (Pair<String, JComponent> tab : new ArrayList<>(tabbedContent.getTabs())) {
        if (Comparing.equal(tab.second, contentComponent)) {
          tabbedContent.removeContent(tab.second);
        }
      }
      if (select) {
        manager.setSelectedContent(tabbedContent, true, true);
      }
      tabbedContent.addContent(contentComponent, tabName, true);
    }

    registerDisposable(tabbedContent, childDisposable, contentComponent);
  }

  private static void registerDisposable(@NotNull Content content,
                                         @Nullable Disposable childDisposable,
                                         @NotNull JComponent contentComponent) {
    if (childDisposable != null) {
      Disposer.register(content, childDisposable);
      assert contentComponent.getClientProperty(DISPOSABLE_KEY) == null;
      contentComponent.putClientProperty(DISPOSABLE_KEY, childDisposable);
      Disposer.register(childDisposable, () -> {
        contentComponent.putClientProperty(DISPOSABLE_KEY, null);
      });
    }
    else {
      Disposable disposableByKey = getDisposable(contentComponent);
      if (disposableByKey != null) {
        Disposer.register(content, disposableByKey);
      }
    }
  }

  @Nullable
  public static TabbedContent findTabbedContent(@NotNull ContentManager manager, @NotNull String groupPrefix) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContent) {
        if (((TabbedContent)content).getTitlePrefix().equals(groupPrefix)) {
          return (TabbedContent)content;
        }
      }
    }
    return null;
  }

  @NotNull
  public static String getFullName(@NotNull String groupPrefix, @NotNull String tabName) {
    return groupPrefix + ": " + tabName;
  }

  /**
   * Searches through all {@link Content simple} and {@link TabbedContent tabbed} contents of the given ContentManager,
   * and selects the one which holds the specified {@code contentComponent}.
   *
   * @return true if the necessary content was found (and thus selected) among content components of the given ContentManager.
   */
  public static boolean selectContent(@NotNull ContentManager manager, @NotNull final JComponent contentComponent, boolean requestFocus) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContentImpl) {
        boolean found = ((TabbedContentImpl)content).findAndSelectContent(contentComponent);
        if (found) {
          manager.setSelectedContent(content, requestFocus);
          return true;
        }
      }
      else if (Comparing.equal(content.getComponent(), contentComponent)) {
        manager.setSelectedContent(content, requestFocus);
        return true;
      }
    }
    return false;
  }

  /**
   * Searches through all {@link Content simple} and {@link TabbedContent tabbed} contents of the given ContentManager,
   * trying to find the first one which matches the given condition.
   */
  @Nullable
  public static JComponent findContentComponent(@NotNull ContentManager manager, @NotNull Condition<? super JComponent> condition) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContentImpl) {
        List<Pair<String, JComponent>> tabs = ((TabbedContentImpl)content).getTabs();
        for (Pair<String, JComponent> tab : tabs) {
          if (condition.value(tab.second)) {
            return tab.second;
          }
        }
      }
      else if (condition.value(content.getComponent())) {
        return content.getComponent();
      }
    }
    return null;
  }

  @Nullable
  private static JComponent findContentComponent(@NotNull TabbedContent tabbedContent, @NotNull Condition<? super JComponent> condition) {
    for (Pair<String, JComponent> tab : tabbedContent.getTabs()) {
      if (condition.value(tab.second)) {
        return tab.second;
      }
    }
    return null;
  }

  /**
   * Closes content with component that matches specified condition.
   *
   * @return true if content was found and closed
   */
  public static boolean closeContentTab(@NotNull ContentManager manager, @NotNull Condition<? super JComponent> condition) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContent && ((TabbedContent)content).hasMultipleTabs()) {
        TabbedContent tabbedContent = (TabbedContent)content;
        JComponent component = findContentComponent(tabbedContent, condition);
        if (component != null) {
          tabbedContent.removeContent(component);
          dispose(component);
          return true;
        }
      }
      else if (condition.value(content.getComponent())) {
        manager.removeContent(content, true);
        return true;
      }
    }
    return false;
  }

  public static int getSelectedTab(@NotNull TabbedContent content) {
    int selectedIndex = content.getSelectedIndex();
    if (selectedIndex != -1) return selectedIndex;

    final JComponent current = content.getComponent();
    int index = 0;
    for (Pair<String, JComponent> tab : content.getTabs()) {
      if (tab.second == current) {
        return index;
      }
      index++;
    }
    return -1;
  }

  public static void updateTabbedContentDisplayName(@NotNull ContentManager manager, @NotNull JComponent contentComponent) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContentImpl) {
        if (((TabbedContentImpl)content).updateName(contentComponent)) {
          return;
        }
      }
      else if (Comparing.equal(content.getComponent(), contentComponent)) {
        String groupPrefix = content.getUserData(Content.TAB_GROUP_NAME_KEY);
        if (groupPrefix != null) {
          content.setDisplayName(getDisplayName(contentComponent, true));
          return;
        }
      }
    }
  }

  public static void mergeTabs(@NotNull ContentManager manager, @NotNull @NonNls String tabPrefix) {
    final Content selectedContent = manager.getSelectedContent();
    final List<Pair<String, JComponent>> tabs = new ArrayList<>();
    int selectedTab = -1;
    List<Content> mergedContent = new ArrayList<>();
    for (Content content : manager.getContents()) {
      if (tabPrefix.equals(content.getUserData(Content.TAB_GROUP_NAME_KEY))) {
        final String label = content.getTabName().substring(tabPrefix.length() + 2);
        final JComponent component = content.getComponent();
        if (content == selectedContent) {
          selectedTab = tabs.size();
        }
        tabs.add(Pair.create(label, component));
        manager.removeContent(content, false);
        content.setComponent(null);
        content.setShouldDisposeContent(false);
        mergedContent.add(content);
      }
    }
    setSplitMode(tabPrefix, false);
    for (int i = 0; i < tabs.size(); i++) {
      final Pair<String, JComponent> tab = tabs.get(i);
      addTabbedContent(manager, tab.second, tabPrefix, tab.first, i == selectedTab);
    }
    mergedContent.forEach(Disposer::dispose);
  }

  public static boolean isSplitMode(@NonNls @NotNull String groupId) {
    return PropertiesComponent.getInstance().getBoolean(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId, false);
  }

  public static void setSplitMode(@NonNls @NotNull String groupId, boolean value) {
    if (value) {
      PropertiesComponent.getInstance().setValue(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId, Boolean.TRUE.toString());
    }
    else {
      PropertiesComponent.getInstance().unsetValue(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId);
    }
  }

  public static final Key<Supplier<@Nls String>> TAB_DISPLAY_NAME = Key.create("tabDisplayName");
  public static final Key<Supplier<@Nls String>> TAB_DISPLAY_PREFIX = Key.create("tabDisplayPrefix");

  @Nullable
  public static <T> T getValue(@NotNull JComponent component, @NotNull Key<Supplier<T>> key) {
    Object value = component.getClientProperty(key);
    if (!(value instanceof Supplier)) {
      return null;
    }
    // noinspection unchecked
    return (T)((Supplier)value).get();
  }

  @Nls
  @Nullable
  public static String getDisplayName(@NotNull JComponent component, boolean withPrefix) {
    String name = getValue(component, TAB_DISPLAY_NAME);
    if (name == null) return null;
    if (!withPrefix) return name;

    String prefix = getDisplayPrefix(component);
    if (prefix == null) return null;
    return getFullName(prefix, name);
  }

  @Nls
  @Nullable
  private static String getDisplayPrefix(@NotNull JComponent component) {
    String prefix = getValue(component, TAB_DISPLAY_PREFIX);
    if (prefix == null) return null;
    return prefix;
  }

  @Nls
  @NotNull
  public static String getDisplayPrefix(@NotNull Content content) {
    String displayPrefix = getDisplayPrefix(content.getComponent());
    if (displayPrefix != null) return displayPrefix;
    if (content instanceof TabbedContent) {
      return ((TabbedContent)content).getTitlePrefix();
    }
    return Objects.requireNonNull(content.getUserData(Content.TAB_GROUP_NAME_KEY));
  }
}
