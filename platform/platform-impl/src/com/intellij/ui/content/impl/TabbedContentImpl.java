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
package com.intellij.ui.content.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class TabbedContentImpl extends ContentImpl implements TabbedContent {
  private final List<Pair<String, JComponent>> myTabs = new ArrayList<>();
  private String myPrefix;

  public TabbedContentImpl(JComponent component, String displayName, boolean isPinnable, String titlePrefix) {
    super(component, displayName, isPinnable);
    myPrefix = titlePrefix;
    addContent(component, displayName, true);
  }

  @Override
  public void addContent(@NotNull JComponent content, @NotNull String name, boolean selectTab) {
    Pair<String, JComponent> tab = Pair.create(name, content);
    if (!myTabs.contains(tab)) {
      myTabs.add(tab);
    }
    if (selectTab && getComponent() != content) {
      setComponent(content);
    }
  }

  @Override
  public void setComponent(JComponent component) {
    JComponent currentComponent = getComponent();
    Container parent = currentComponent == null ? null : currentComponent.getParent();
    if (parent != null) {
      parent.remove(currentComponent);
      parent.add(component);
    }

    super.setComponent(component);
  }

  @Override
  public void removeContent(@NotNull JComponent content) {
    Pair<String, JComponent> toRemove = null;
    for (Pair<String, JComponent> tab : myTabs) {
      if (tab.second == content) {
        toRemove = tab;
        break;
      }
    }
    int index = myTabs.indexOf(toRemove);
    if (index != -1) {
      myTabs.remove(index);
      index = index > 0 ? index - 1 : index;
      if (index < myTabs.size()) {
        selectContent(index);
      }
    }
  }

  @Override
  public void renameContent(@NotNull JComponent tab, @NotNull String newTabName) {
    Pair<String, JComponent> toRemove = null;
    for (Pair<String, JComponent> existingTab : myTabs) {
      if (existingTab.second == tab) {
        toRemove = existingTab;
        break;
      }
    }
    int index = myTabs.indexOf(toRemove);
    if (index != -1) {
      myTabs.remove(index);
    }
    myTabs.add(Pair.create(newTabName, tab));
    if (getComponent() == tab) {
      super.setDisplayName(newTabName);
    }
  }

  @Override
  public String getDisplayName() {
    return getTabName();
  }

  @Override
  public void selectContent(int index) {
    Pair<String, JComponent> tab = myTabs.get(index);
    setDisplayName(tab.first);
    setComponent(tab.second);
  }

  public boolean findAndSelectContent(@NotNull JComponent contentComponent) {
    String tabName = findTabNameByComponent(contentComponent);
    if (tabName != null) {
      setDisplayName(tabName);
      setComponent(contentComponent);
      return true;
    }
    return false;
  }

  @Override
  public String getTabName() {
    String selected = findTabNameByComponent(getComponent());
    if (myPrefix != null) {
      selected = myPrefix + ": " + selected;
    }
    return selected;
  }

  private String findTabNameByComponent(JComponent c) {
    for (Pair<String, JComponent> tab : myTabs) {
      if (tab.second == c) {
        return tab.first;
      }
    }
    return null;
  }

  @Override
  public List<Pair<String, JComponent>> getTabs() {
    return Collections.unmodifiableList(myTabs);
  }

  @Override
  public String getTitlePrefix() {
    return myPrefix;
  }

  @Override
  public void setTitlePrefix(String titlePrefix) {
    myPrefix = titlePrefix;
  }

  @Nullable
  @Override
  public String getTabNameWithoutPrefix(String fullTabName) {
    int titlePrefixLength = getTitlePrefix().length() + 2;
    if (fullTabName.startsWith(getTitlePrefix())) {
      return fullTabName.substring(titlePrefixLength);
    }
    return null;
  }

  @Override
  public void split() {
    List<Pair<String, JComponent>> copy = new ArrayList<>(myTabs);
    int selectedTab = ContentUtilEx.getSelectedTab(this);
    ContentManager manager = getManager();
    String prefix = getTitlePrefix();
    manager.removeContent(this, false);
    PropertiesComponent.getInstance().setValue(SPLIT_PROPERTY_PREFIX + prefix, Boolean.TRUE.toString());
    for (int i = 0; i < copy.size(); i++) {
      final boolean select = i == selectedTab;
      final JComponent component = copy.get(i).second;
      final String tabName = copy.get(i).first;
      ContentUtilEx.addTabbedContent(manager, component, prefix, tabName, select);
    }
    Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    super.dispose();
    myTabs.clear();
  }
}
