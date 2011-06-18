/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.GuiUtils;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: zajac
 * Date: 16.06.11
 * Time: 21:26
 * To change this template use File | Settings | File Templates.
 */
public class XMasterBreakpointPanel<B extends XBreakpoint<?>> extends XBreakpointPropertiesSubPanel<B> {
  private JPanel myMasterBreakpointComboBoxPanel;
  private JPanel myAfterBreakpointHitPanel;
  private JRadioButton myLeaveEnabledRadioButton;
  private JPanel myContentPane;
  private JPanel myMainPanel;

  private ComboBox myMasterBreakpointComboBox;
  private XDependentBreakpointManager myDependentBreakpointManager;

  private static class BreakpointsListCellRenderer<B extends XBreakpoint<?>> extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        B breakpoint = (B)value;
        setText(XBreakpointUtil.getDisplayText(breakpoint));
        setIcon(breakpoint.getType().getEnabledIcon());
      }
      else {
        setText(XDebuggerBundle.message("xbreakpoint.master.breakpoint.none"));
        setIcon(null);
      }
      return component;
    }
  }

  @Override
  public void init(Project project, XBreakpointManager breakpointManager, @NotNull B breakpoint) {
    super.init(project, breakpointManager, breakpoint);
    myDependentBreakpointManager = ((XBreakpointManagerImpl)breakpointManager).getDependentBreakpointManager();
    myMasterBreakpointComboBox = new ComboBox(300);
    myMasterBreakpointComboBoxPanel.add(myMasterBreakpointComboBox, BorderLayout.CENTER);
    myMasterBreakpointComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateAfterBreakpointHitPanel();
      }
    });
    myMasterBreakpointComboBox.setRenderer(new BreakpointsListCellRenderer<B>());
    fillMasterBreakpointComboBox();
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
    if (!showAllOptions && masterBreakpoint == null) {
      myMainPanel.setVisible(false);
      return true;
    }
    return false;
  }

  private void fillMasterBreakpointComboBox() {
    myMasterBreakpointComboBox.removeAllItems();
    myMasterBreakpointComboBox.addItem(null);
    for (B breakpoint : myBreakpointManager.getBreakpoints(XBreakpointUtil.getType(myBreakpoint))) {
      if (breakpoint != myBreakpoint) {
        myMasterBreakpointComboBox.addItem(breakpoint);
      }
    }
  }


  private void updateAfterBreakpointHitPanel() {
    boolean enable = myMasterBreakpointComboBox.getSelectedItem() != null;
    GuiUtils.enableChildren(enable, myAfterBreakpointHitPanel);
  }

  @Override
  void loadProperties() {
    XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
    if (masterBreakpoint != null) {
      myMasterBreakpointComboBox.setSelectedItem(masterBreakpoint);
      myLeaveEnabledRadioButton.setSelected(myDependentBreakpointManager.isLeaveEnabled(myBreakpoint));
    }
    updateAfterBreakpointHitPanel();

  }

  @Override
  void saveProperties() {
    XBreakpoint<?> masterBreakpoint = (XBreakpoint<?>)myMasterBreakpointComboBox.getSelectedItem();
    if (masterBreakpoint == null) {
      myDependentBreakpointManager.clearMasterBreakpoint(myBreakpoint);
    }
    else {
      myDependentBreakpointManager.setMasterBreakpoint(myBreakpoint, masterBreakpoint, myLeaveEnabledRadioButton.isSelected());
    }
  }
}
