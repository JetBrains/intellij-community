// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content.impl;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.TabDescriptor;
import com.intellij.ui.content.TabGroupId;
import com.intellij.ui.content.TabbedContent;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class TabbedContentImpl extends ContentImpl implements TabbedContent {
  @NotNull
  private final List<TabDescriptor> myTabs = new ArrayList<>();
  @NotNull
  private final TabGroupId myId;

  /**
   * @deprecated use {@link TabbedContentImpl#TabbedContentImpl(TabGroupId, TabDescriptor, boolean)} instead
   * as it allows to set tab group id separately from display name.
   */
  @Deprecated(forRemoval = true)
  public TabbedContentImpl(JComponent component, @NotNull @NlsContexts.TabTitle String displayName, boolean isPinnable, @NotNull @NonNls String titlePrefix) {
    this(new TabGroupId(titlePrefix, titlePrefix), new TabDescriptor(component, displayName), isPinnable); //NON-NLS
  }

  public TabbedContentImpl(@NotNull TabGroupId id, @NotNull TabDescriptor tab, boolean isPinnable) {
    super(tab.getComponent(), id.getDisplayName(tab), isPinnable);
    myId = id;
    myTabs.add(tab);
    Disposer.register(this, tab);
  }

  @Nullable
  private TabDescriptor findTab(@NotNull JComponent c) {
    for (TabDescriptor tab : myTabs) {
      if (tab.getComponent() == c) {
        return tab;
      }
    }
    return null;
  }

  @Nullable
  private TabDescriptor selectedTab() {
    return findTab(getComponent());
  }

  private int indexOf(@NotNull JComponent c) {
    for (int i = 0; i < myTabs.size(); i++) {
      if (myTabs.get(i).getComponent() == c) return i;
    }
    return -1;
  }

  private void selectTab(@NotNull TabDescriptor tab) {
    setDisplayName(myId.getDisplayName(tab));
    setComponent(tab.getComponent());
  }

  @Override
  public void addContent(@NotNull JComponent content, @NotNull String name, boolean selectTab) {
    addContent(new TabDescriptor(content, name), selectTab);
  }

  @Override
  public void addContent(@NotNull TabDescriptor tab, boolean selectTab) {
    Disposer.register(this, tab);
    if (!myTabs.contains(tab)) {
      myTabs.add(tab);
    }
    if (selectTab && getComponent() != tab.getComponent()) {
      selectTab(tab);
    }
  }

  @NotNull
  @Override
  public TabGroupId getId() {
    return myId;
  }

  @Nls
  @NotNull
  @Override
  public String getTitlePrefix() {
    return myId.getDisplayName();
  }

  @Override
  public void setComponent(JComponent component) {
    JComponent currentComponent = getComponent();
    Container parent = currentComponent.getParent();
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
      Disposer.dispose(myTabs.remove(index));
      index = index > 0 ? index - 1 : index;
      if (index < myTabs.size()) {
        selectContent(index);
      }
    }
  }

  @Nls
  @Override
  public String getDisplayName() {
    TabDescriptor selectedTab = selectedTab();
    if (selectedTab == null) return myId.getDisplayName();
    return myId.getDisplayName(selectedTab);
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
    TabDescriptor tab = findTab(contentComponent);
    if (tab != null) {
      selectTab(tab);
      return true;
    }
    return false;
  }

  @Override
  public String getTabName() {
    return getDisplayName();
  }

  @NotNull
  @Override
  public List<Pair<String, JComponent>> getTabs() {
    return ContainerUtil.map2List(myTabs, tab -> Pair.create(tab.getDisplayName(), tab.getComponent()));
  }

  @Override
  public boolean hasMultipleTabs() {
    return myTabs.size() > 1;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    if (key.equals(TAB_GROUP_ID_KEY)) return (T)myId;
    if (key.equals(TAB_DESCRIPTOR_KEY)) return (T)selectedTab();
    return super.getUserData(key);
  }

  @Override
  public void split() {
    ContentManager manager = getManager();
    if (manager == null) return;

    boolean selected = manager.isSelected(this);
    TabDescriptor selectedTab = selectedTab();

    List<TabDescriptor> tabsCopy = new ArrayList<>(myTabs);

    manager.removeContent(this, false);
    ContentUtilEx.setSplitMode(myId.getId(), true);

    for (TabDescriptor tab : tabsCopy) {
      ContentUtilEx.addSplitTabbedContent(manager, myId, tab, selected && tab == selectedTab);
    }

    Disposer.dispose(this);
  }
}
