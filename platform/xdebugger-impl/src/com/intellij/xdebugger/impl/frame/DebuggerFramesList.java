// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.pom.Navigatable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Position;
import java.awt.*;

public abstract class DebuggerFramesList extends JBList implements OccurenceNavigator {
  public DebuggerFramesList() {
    super(new CollectionListModel());
  }

  protected void doInit() {
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(createListRenderer());
    getEmptyText().setText(XDebuggerBundle.message("debugger.frames.not.available"));
  }

  @Override
  public void setModel(ListModel model) {
    // do not allow to change model (e.g. to FilteringListModel)
  }

  @Override
  public CollectionListModel getModel() {
    return (CollectionListModel)super.getModel();
  }

  public void clear() {
    getModel().removeAll();
  }

  public int getElementCount() {
    return getModel().getSize();
  }

  @NotNull
  @Override
  public String getNextOccurenceActionName() {
    return XDebuggerBundle.message("action.next.frame.text");
  }

  @NotNull
  @Override
  public String getPreviousOccurenceActionName() {
    return XDebuggerBundle.message("action.previous.frame.text");
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    setSelectedIndex(getSelectedIndex() + 1);
    return getOccurenceInfo();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    setSelectedIndex(getSelectedIndex() - 1);
    return getOccurenceInfo();
  }

  @NotNull
  private OccurenceInfo getOccurenceInfo() {
    Navigatable navigatable = getSelectedFrameNavigatable();
    return new OccurenceInfo(navigatable, -1, -1);
  }

  protected @Nullable Navigatable getSelectedFrameNavigatable() {
    return null;
  }

  @Override
  public boolean hasNextOccurence() {
    return getSelectedIndex() < getElementCount() - 1;
  }

  @Override
  public boolean hasPreviousOccurence() {
    return getSelectedIndex() > 0;
  }

  protected abstract ListCellRenderer createListRenderer();

  @Override
  public int getNextMatch(String prefix, int startIndex, Position.Bias bias) {
    return -1; // disable built-in search completely to avoid calling toString for every item
  }

  @Override
  public int locationToIndex(Point location) {
    if (location.y <= getPreferredSize().height) {
      return super.locationToIndex(location);
    }
    return -1;
  }
}
