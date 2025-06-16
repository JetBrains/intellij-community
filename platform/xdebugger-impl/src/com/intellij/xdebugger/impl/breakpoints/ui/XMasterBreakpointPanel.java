// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class XMasterBreakpointPanel extends XBreakpointPropertiesSubPanel {
  private JPanel myMasterBreakpointComboBoxPanel;
  private JPanel myAfterBreakpointHitPanel;
  private JRadioButton myLeaveEnabledRadioButton;
  @SuppressWarnings("UnusedDeclaration")
  private JPanel myContentPane;
  private JPanel myMainPanel;

  private BreakpointChooser myMasterBreakpointChooser;
  private XDependentBreakpointManagerProxy myDependentBreakpointManager;

  private static List<BreakpointItem> getBreakpointItemsExceptMy(
    XBreakpointManagerProxy breakpointManager,
    XBreakpointProxy breakpoint
  ) {
    List<BreakpointItem> items = breakpointManager.getAllBreakpointItems();
    for (BreakpointItem item : items) {
      if (Objects.equals(item.getBreakpoint(), breakpoint)) {
        items.remove(item);
        break;
      }
    }
    Collections.sort(items);
    items.add(0, new BreakpointNoneItem());
    return items;
  }

  @Override
  public void init(Project project, @NotNull XBreakpointProxy breakpoint) {
    super.init(project, breakpoint);
    XBreakpointManagerProxy breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project);
    myDependentBreakpointManager = breakpointManager.getDependentBreakpointManager();
    myMasterBreakpointChooser = new BreakpointChooser(project, new BreakpointChooser.Delegate() {
      @Override
      public void breakpointChosen(Project project, BreakpointItem breakpointItem) {
        updateAfterBreakpointHitPanel();
      }
    }, null, getBreakpointItemsExceptMy(breakpointManager, breakpoint));

    myMasterBreakpointComboBoxPanel.add(myMasterBreakpointChooser.getComponent(), BorderLayout.CENTER);
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    XBreakpointProxy masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
    if (!showAllOptions && masterBreakpoint == null) {
      myMainPanel.setVisible(false);
      return true;
    }
    return false;
  }

  private void updateAfterBreakpointHitPanel() {
    boolean enable = myMasterBreakpointChooser.getSelectedBreakpoint() != null;
    GuiUtils.enableChildren(enable, myAfterBreakpointHitPanel);
  }

  @Override
  void loadProperties() {
    XBreakpointProxy masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
    if (masterBreakpoint != null) {
      myMasterBreakpointChooser.setSelectedBreakpoint(masterBreakpoint);
      myLeaveEnabledRadioButton.setSelected(myDependentBreakpointManager.isLeaveEnabled(myBreakpoint));
    }
    updateAfterBreakpointHitPanel();
  }

  @Override
  void saveProperties() {
    XBreakpointProxy masterBreakpoint = myMasterBreakpointChooser.getSelectedBreakpoint();
    if (masterBreakpoint == null) {
      myDependentBreakpointManager.clearMasterBreakpoint(myBreakpoint);
    }
    else {
      myDependentBreakpointManager.setMasterBreakpoint(myBreakpoint, masterBreakpoint, myLeaveEnabledRadioButton.isSelected());
    }
  }

  public void setDetailView(DetailView detailView) {
    if (myMasterBreakpointChooser != null) {
      myMasterBreakpointChooser.setDetailView(detailView);
    }
  }

  public void hide() {
    myContentPane.getParent().remove(myContentPane);
  }
}
