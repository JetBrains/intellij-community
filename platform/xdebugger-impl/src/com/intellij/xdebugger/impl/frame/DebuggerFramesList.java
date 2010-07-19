/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.OccurenceNavigator;
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
    super(new DefaultListModel());
    getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setCellRenderer(createListRenderer());
    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
          onFrameChanged(getSelectedValue());
        }
      }
    });
  }

  public DefaultListModel getModel() {
    return (DefaultListModel)super.getModel();
  }

  public void clear() {
    getModel().clear();
  }

  public int getElementCount() {
    return getModel().getSize();
  }

  public String getNextOccurenceActionName() {
    return XDebuggerBundle.message("action.next.frame.text");
  }

  public String getPreviousOccurenceActionName() {
    return XDebuggerBundle.message("action.previous.frame.text");
  }

  public OccurenceInfo goNextOccurence() {
    setSelectedIndex(getSelectedIndex() + 1);
    return createInfo();
  }

  public OccurenceInfo goPreviousOccurence() {
    setSelectedIndex(getSelectedIndex() - 1);
    return createInfo();
  }

  private OccurenceInfo createInfo() {
    return OccurenceInfo.position(getSelectedIndex(), getElementCount());
  }

  public boolean hasNextOccurence() {
    return getSelectedIndex() < getElementCount() - 1;
  }

  public boolean hasPreviousOccurence() {
    return getSelectedIndex() > 0;
  }

  protected abstract ListCellRenderer createListRenderer();

  protected abstract void onFrameChanged(final Object selectedValue);
}
