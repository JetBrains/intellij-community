// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.*;
import com.intellij.ui.content.*;
import com.intellij.ui.content.impl.TabbedContentImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public final class ContentUtilEx extends ContentsUtil {
  /**
   * @deprecated use {@link ContentUtilEx#addTabbedContent(ContentManager, JComponent, String, Supplier, Supplier, boolean, Disposable)}
   * as it allows setting tab group id separately from display name.
   */
  @Deprecated
  public static void addTabbedContent(@NotNull ContentManager manager,
                                      @NotNull JComponent contentComponent,
                                      @NotNull @NonNls String groupPrefix,
                                      @NotNull @NlsContexts.TabTitle String tabName,
                                      boolean select,
                                      @Nullable Disposable childDisposable) {
    addTabbedContent(manager, new TabGroupId(groupPrefix, groupPrefix), //NON-NLS one value for id and display name, nothing to be done
                     new TabDescriptor(contentComponent, () -> tabName, childDisposable), select);
  }

  public static void addTabbedContent(@NotNull ContentManager manager,
                                      @NotNull JComponent contentComponent,
                                      @NotNull @NonNls String groupId,
                                      @NotNull Supplier<@NlsContexts.TabTitle String> groupDisplayName,
                                      @NotNull Supplier<@NlsContexts.TabTitle String> tabDisplayName,
                                      boolean select,
                                      @Nullable Disposable childDisposable) {
    addTabbedContent(manager, new TabGroupId(groupId, groupDisplayName),
                     new TabDescriptor(contentComponent, tabDisplayName, childDisposable), select);
  }

  public static void addTabbedContent(@NotNull ContentManager manager, @NotNull TabGroupId tabGroupId, @NotNull TabDescriptor tab,
                                      boolean select) {
    if (isSplitMode(tabGroupId)) {
      addSplitTabbedContent(manager, tabGroupId, tab, select);
    }
    else {
      addMergedTabbedContent(manager, tabGroupId, tab, select);
    }
  }

  public static void addSplitTabbedContent(@NotNull ContentManager manager,
                                           @NotNull TabGroupId tabGroupId,
                                           @NotNull TabDescriptor tab,
                                           boolean select) {
    Content content = ContentFactory.SERVICE.getInstance().createContent(tab.getComponent(), tabGroupId.getDisplayName(tab),
                                                                         true);
    content.setTabName(tabGroupId.getDisplayName(tab));
    content.putUserData(Content.TABBED_CONTENT_KEY, Boolean.TRUE);
    content.putUserData(Content.TAB_GROUP_ID_KEY, tabGroupId);
    content.putUserData(Content.TAB_DESCRIPTOR_KEY, tab);

    Disposer.register(content, tab);

    addContent(manager, content, select);
  }

  private static void addMergedTabbedContent(@NotNull ContentManager manager,
                                             @NotNull TabGroupId tabGroupId,
                                             @NotNull TabDescriptor tab,
                                             boolean select) {
    TabbedContent tabbedContent = findTabbedContent(manager, tabGroupId.getId());
    if (tabbedContent != null) {
      if (select) {
        manager.setSelectedContent(tabbedContent, true, true);
      }
      tabbedContent.addContent(tab, true);
      return;
    }

    createMergedTabbedContent(manager, tabGroupId, Collections.singletonList(tab), tab, select);
  }

  private static void createMergedTabbedContent(@NotNull ContentManager manager,
                                                @NotNull TabGroupId tabGroupId,
                                                @NotNull List<TabDescriptor> tabs,
                                                @Nullable TabDescriptor tabToSelect,
                                                boolean selectContent) {
    Iterator<TabDescriptor> iterator = tabs.iterator();
    TabbedContent tabbedContent = new TabbedContentImpl(tabGroupId, iterator.next(), true);
    addContent(manager, tabbedContent, selectContent);
    while (iterator.hasNext()) {
      TabDescriptor tab = iterator.next();
      tabbedContent.addContent(tab, tab == tabToSelect);
    }
  }

  @Nullable
  public static TabbedContent findTabbedContent(@NotNull ContentManager manager, @NotNull @NonNls String id) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContent) {
        if (((TabbedContent)content).getId().getId().equals(id)) {
          return (TabbedContent)content;
        }
      }
    }
    return null;
  }

  @NotNull
  @NlsContexts.TabTitle
  public static String getFullName(@NotNull @NlsContexts.TabTitle String groupPrefix, @NotNull @NlsContexts.TabTitle String tabName) {
    if (tabName.isEmpty()) return groupPrefix;
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
      if (content instanceof TabbedContent) {
        JComponent component = findContentComponent((TabbedContent)content, condition);
        if (component != null) return component;
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
      if (Comparing.equal(content.getComponent(), contentComponent)) {
        TabGroupId groupId = content.getUserData(Content.TAB_GROUP_ID_KEY);
        TabDescriptor tab = content.getUserData(Content.TAB_DESCRIPTOR_KEY);
        if (groupId != null && tab != null) {
          content.setDisplayName(groupId.getDisplayName(tab));
          return;
        }
      }
    }
  }

  public static void mergeTabs(@NotNull ContentManager manager, @NotNull TabGroupId groupId) {
    List<TabDescriptor> tabs = new ArrayList<>();
    Content selectedContent = manager.getSelectedContent();
    TabDescriptor selectedTab = null;
    List<Content> mergedContent = new ArrayList<>();
    for (Content content : manager.getContents()) {
      if (groupId.equals(content.getUserData(Content.TAB_GROUP_ID_KEY))) {
        TabDescriptor tab = content.getUserData(Content.TAB_DESCRIPTOR_KEY);
        if (tab == null) {
          tab = new TabDescriptor(content.getComponent(), content.getDisplayName().substring(groupId.getDisplayName().length() + 2));
        }
        if (content == selectedContent) {
          selectedTab = tab;
        }
        tabs.add(tab);
        manager.removeContent(content, false);
        content.setComponent(null);
        content.setShouldDisposeContent(false);
        mergedContent.add(content);
      }
    }

    setSplitMode(groupId.getId(), false);
    createMergedTabbedContent(manager, groupId, tabs, selectedTab, selectedTab != null);

    mergedContent.forEach(Disposer::dispose);
  }

  public static boolean isSplitMode(@NotNull TabGroupId tabGroupId) {
    return isSplitMode(tabGroupId.getId(), tabGroupId.getSplitByDefault());
  }

  public static boolean isSplitMode(@NonNls @NotNull String groupId, boolean defaultValue) {
    return PropertiesComponent.getInstance().getBoolean(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId, defaultValue);
  }

  public static void setSplitMode(@NonNls @NotNull String groupId, boolean value) {
    if (value) {
      PropertiesComponent.getInstance().setValue(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId, Boolean.TRUE.toString());
    }
    else {
      PropertiesComponent.getInstance().unsetValue(TabbedContent.SPLIT_PROPERTY_PREFIX + groupId);
    }
  }
}
