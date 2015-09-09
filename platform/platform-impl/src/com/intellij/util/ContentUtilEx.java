/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  public static void addTabbedContent(ContentManager manager, JComponent contentComponent, String groupPrefix, String tabName, boolean select) {
    addTabbedContent(manager, contentComponent, groupPrefix, tabName, select, null);
  }

  public static void addTabbedContent(ContentManager manager, JComponent contentComponent, String groupPrefix, String tabName, boolean select, @Nullable Disposable childDisposable) {
    if (PropertiesComponent.getInstance().getBoolean(TabbedContent.SPLIT_PROPERTY_PREFIX + groupPrefix)) {
      final Content content = ContentFactory.SERVICE.getInstance().createContent(contentComponent, groupPrefix + ": " + tabName, true);
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
      return;
    }

    TabbedContent tabbedContent = null;
    for (Content content : manager.getContents()) {
      if (content instanceof TabbedContent && content.getTabName().startsWith(groupPrefix + ": ")) {
        tabbedContent = (TabbedContent)content;
        break;
      }
    }

    if (tabbedContent == null) {
      final Disposable disposable = Disposer.newDisposable();
      tabbedContent = new TabbedContentImpl(contentComponent, tabName, true, groupPrefix);
      ContentsUtil.addOrReplaceContent(manager, tabbedContent, select);
      Disposer.register(tabbedContent, disposable);
    } else {
      for (Pair<String, JComponent> tab : new ArrayList<Pair<String, JComponent>>(tabbedContent.getTabs())) {
        if (Comparing.equal(tab.second, contentComponent)) {
          tabbedContent.removeContent(tab.second);
        }
      }
      if (select) {
        manager.setSelectedContent(tabbedContent, true, true);
      }
      tabbedContent.addContent(contentComponent, tabName, true);
    }

    if (childDisposable != null) {
      Disposer.register(tabbedContent, childDisposable);
    }
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
  public static JComponent findContentComponent(@NotNull ContentManager manager, @NotNull Condition<JComponent> condition) {
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

  public static int getSelectedTab(@NotNull TabbedContent content) {
    final JComponent current = content.getComponent();
    int index = 0;
    for (Pair<String,JComponent> tab : content.getTabs()) {
      if (tab.second == current) {
        return index;
      }
      index++;
    }
    return -1;
  }
}
