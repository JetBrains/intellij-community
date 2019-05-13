// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.ui.content.impl.TabbedContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

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
    if (PropertiesComponent.getInstance().getBoolean(TabbedContent.SPLIT_PROPERTY_PREFIX + groupPrefix)) {
      final Content content = ContentFactory.SERVICE.getInstance().createContent(contentComponent, getFullName(groupPrefix, tabName), true);
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
      ContentsUtil.addOrReplaceContent(manager, tabbedContent, select);
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
      Disposer.register(childDisposable, () -> contentComponent.putClientProperty(DISPOSABLE_KEY, null));
    }
    else {
      Object disposableByKey = contentComponent.getClientProperty(DISPOSABLE_KEY);
      if (disposableByKey instanceof Disposable) {
        Disposer.register(content, (Disposable)disposableByKey);
      }
    }
  }

  @Nullable
  public static TabbedContent findTabbedContent(@NotNull ContentManager manager, @NotNull String groupPrefix) {
    TabbedContent tabbedContent = null;
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContent && content.getTabName().startsWith(getFullPrefix(groupPrefix))) {
        tabbedContent = (TabbedContent)content;
        break;
      }
    }
    return tabbedContent;
  }

  public static boolean isContentTab(@NotNull Content content, @NotNull String groupPrefix) {
    return (content instanceof TabbedContent && content.getTabName().startsWith(getFullPrefix(groupPrefix))) ||
           groupPrefix.equals(content.getUserData(Content.TAB_GROUP_NAME_KEY));
  }

  @NotNull
  public static String getFullName(@NotNull String groupPrefix, @NotNull String tabName) {
    return getFullPrefix(groupPrefix) + tabName;
  }

  @NotNull
  private static String getFullPrefix(@NotNull String groupPrefix) {
    return groupPrefix + ": ";
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

  public static boolean renameTabbedContent(@NotNull ContentManager manager,
                                            @NotNull JComponent contentComponent,
                                            @NotNull String newName) {
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContentImpl) {
        if (((TabbedContentImpl)content).rename(contentComponent, newName)) {
          return true;
        }
      }
      else if (Comparing.equal(content.getComponent(), contentComponent)) {
        String groupPrefix = content.getUserData(Content.TAB_GROUP_NAME_KEY);
        if (groupPrefix != null) {
          content.setDisplayName(getFullName(groupPrefix, newName));
          return true;
        }
      }
    }
    return false;
  }
}
