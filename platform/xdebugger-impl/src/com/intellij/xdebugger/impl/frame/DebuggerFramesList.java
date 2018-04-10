/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * @author nik
 */
public abstract class DebuggerFramesList extends JBList implements OccurenceNavigator {
  public DebuggerFramesList() {
    super(new CollectionListModel());
  }

  protected void doInit() {
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(createListRenderer());
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          onFrameChanged(getSelectedValue());
        }
      }
    });

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

  @Override
  public String getNextOccurenceActionName() {
    return XDebuggerBundle.message("action.next.frame.text");
  }

  @Override
  public String getPreviousOccurenceActionName() {
    return XDebuggerBundle.message("action.previous.frame.text");
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    setSelectedIndex(getSelectedIndex() + 1);
    return createInfo();
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    setSelectedIndex(getSelectedIndex() - 1);
    return createInfo();
  }

  private OccurenceInfo createInfo() {
    return OccurenceInfo.position(getSelectedIndex(), getElementCount());
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

  protected abstract void onFrameChanged(final Object selectedValue);
}
