// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

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
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public final class TabbedContentImpl extends ContentImpl implements TabbedContent {
  private final List<Pair<String, JComponent>> myTabs = new ArrayList<>();
  @NotNull
  private String myPrefix;

  public TabbedContentImpl(JComponent component, @NotNull String displayName, boolean isPinnable, @NotNull String titlePrefix) {
    super(component, displayName, isPinnable);
    myPrefix = titlePrefix;
    addContent(component, displayName, true);
  }

  @Nullable
  private Pair<String, JComponent> findTab(@NotNull JComponent c) {
    for (Pair<String, JComponent> tab : myTabs) {
      if (tab.second == c) {
        return tab;
      }
    }
    return null;
  }

  @Nullable
  private Pair<String, JComponent> selectedTab() {
    return findTab(getComponent());
  }

  private int indexOf(@NotNull JComponent c) {
    for (int i = 0; i < myTabs.size(); i++) {
      if (myTabs.get(i).second == c) return i;
    }
    return -1;
  }

  private void selectTab(@NotNull Pair<String, JComponent> tab) {
    setDisplayName(ContentUtilEx.getFullName(myPrefix, tab.first));
    setComponent(tab.second);
  }

  @Override
  public void addContent(@NotNull JComponent content, @NotNull String name, boolean selectTab) {
    Pair<String, JComponent> tab = Pair.create(name, content);
    if (!myTabs.contains(tab)) {
      myTabs.add(tab);
    }
    if (selectTab && getComponent() != content) {
      selectTab(tab);
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
    int index = indexOf(content);
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
    selectTab(myTabs.get(index));
  }

  @Override
  public int getSelectedIndex() {
    return indexOf(getComponent());
  }

  public boolean findAndSelectContent(@NotNull JComponent contentComponent) {
    Pair<String, JComponent> tab = findTab(contentComponent);
    if (tab != null) {
      selectTab(tab);
      return true;
    }
    return false;
  }

  @Override
  public String getTabName() {
    return ContentUtilEx.getFullName(myPrefix, Objects.requireNonNull(selectedTab()).first);
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
    ContentManager manager = Objects.requireNonNull(getManager());
    String prefix = getTitlePrefix();
    manager.removeContent(this, false);
    ContentUtilEx.setSplitMode(prefix, true);
    for (int i = 0; i < copy.size(); i++) {
      final boolean select = i == selectedTab;
      final JComponent component = copy.get(i).second;
      final String tabName = copy.get(i).first;
      ContentUtilEx.addTabbedContent(manager, component, prefix, tabName, select);
    }
    Disposer.dispose(this);
  }

  public boolean rename(@NotNull JComponent component, @NotNull String newName) {
    Pair<String, JComponent> tab = findTab(component);
    if (tab == null) return false;
    if (newName.equals(tab.first)) return true;

    int index = myTabs.indexOf(tab);
    myTabs.set(index, new Pair<>(newName, component));
    if (getComponent() == component) {
      setDisplayName(ContentUtilEx.getFullName(myPrefix, newName));
    }

    return true;
  }
}
