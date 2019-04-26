// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
  @NotNull
  private String myPrefix;

  public TabbedContentImpl(JComponent component, @NotNull String displayName, boolean isPinnable, @NotNull String titlePrefix) {
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
  public String getDisplayName() {
    return getTabName();
  }

  @Override
  public void selectContent(int index) {
    Pair<String, JComponent> tab = myTabs.get(index);
    setDisplayName(tab.first);
    setComponent(tab.second);
  }

  @Override
  public int getSelectedIndex() {
    JComponent selected = getComponent();
    for (int i = 0; i < myTabs.size(); i++) {
      if (myTabs.get(i).second == selected) return i;
    }
    return -1;
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
    return myPrefix + ": " + findTabNameByComponent(getComponent());
  }

  private String findTabNameByComponent(JComponent c) {
    for (Pair<String, JComponent> tab : myTabs) {
      if (tab.second == c) {
        return tab.first;
      }
    }
    return null;
  }

  @NotNull
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

  public boolean rename(@NotNull JComponent component, @NotNull String newName) {
    Pair<String, JComponent> tab = ContainerUtil.find(myTabs, pair -> pair.second == component);
    if (tab == null) return false;
    if (newName.equals(tab.first)) return true;

    int index = myTabs.indexOf(tab);
    myTabs.set(index, new Pair<>(newName, component));
    if (getComponent() == component) {
      setDisplayName(newName);
    }

    return true;
  }

  @Override
  public void dispose() {
    super.dispose();
    myTabs.clear();
  }
}
