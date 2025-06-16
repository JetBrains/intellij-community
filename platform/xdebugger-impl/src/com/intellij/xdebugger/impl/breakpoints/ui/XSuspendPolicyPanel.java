// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

@ApiStatus.Internal
public class XSuspendPolicyPanel extends XBreakpointPropertiesSubPanel {
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAll;
  private JRadioButton mySuspendThread;

  private JPanel myContentPane;
  private JButton myMakeDefaultButton;
  private JPanel myMakeDefaultPanel;

  private ButtonGroup mySuspendPolicyGroup;

  public interface Delegate {
    void showActionOptionsIfNeeded();
  }

  private Delegate myDelegate;

  @Override
  public void init(Project project, @NotNull XBreakpointProxy breakpoint) {
    super.init(project, breakpoint);

    mySuspendCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        boolean selected = mySuspendCheckBox.isSelected();

        if (myBreakpoint.getType().isSuspendThreadSupported()) {
          changeEnableState(selected);
        }

        showActionOptionsIfNeeded(selected);
      }
    });

    if (!myBreakpoint.getType().isSuspendThreadSupported()) {
      return;
    }

    updateSuspendPolicyFont();

    ItemListener suspendPolicyChangeListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateMakeDefaultEnableState();
      }
    };
    updateMakeDefaultEnableState();

    mySuspendAll.addItemListener(suspendPolicyChangeListener);
    mySuspendThread.addItemListener(suspendPolicyChangeListener);

    myMakeDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        SuspendPolicy suspendPolicy = getSelectedSuspendPolicy();
        myBreakpointType.setDefaultSuspendPolicy(suspendPolicy);
        updateSuspendPolicyFont();

        JRadioButton comp = SuspendPolicy.THREAD == suspendPolicy ? mySuspendThread : mySuspendAll;
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(comp, true));

        myMakeDefaultButton.setEnabled(false);
      }
    });
  }

  // If the breakpoint is not suspending, it's reasonable to show other available action options.
  private void showActionOptionsIfNeeded(boolean suspendIsSelected) {
    if (myDelegate != null && !suspendIsSelected) {
      myDelegate.showActionOptionsIfNeeded();
    }
  }

  private void updateMakeDefaultEnableState() {
    boolean enabled = !getSelectedSuspendPolicy().equals(getDefaultSuspendPolicy());
    ((CardLayout)myMakeDefaultPanel.getLayout()).show(myMakeDefaultPanel, enabled ? "Show" : "Hide");
    myMakeDefaultButton.setVisible(enabled);
    myMakeDefaultButton.setEnabled(enabled);
  }

  private void updateSuspendPolicyFont() {
    SuspendPolicy defaultPolicy = getDefaultSuspendPolicy();
    Font font = mySuspendAll.getFont().deriveFont(Font.PLAIN);
    Font boldFont = font.deriveFont(Font.BOLD);

    mySuspendAll.setFont(SuspendPolicy.ALL.equals(defaultPolicy) ? boldFont : font);
    mySuspendThread.setFont(SuspendPolicy.THREAD.equals(defaultPolicy) ? boldFont : font);
  }

  private SuspendPolicy getDefaultSuspendPolicy() {
    return myBreakpointType.getDefaultSuspendPolicy();
  }

  private void changeEnableState(boolean selected) {
    mySuspendAll.setEnabled(selected);
    mySuspendThread.setEnabled(selected);
    if (selected) {
      updateMakeDefaultEnableState();
    }
    else {
      myMakeDefaultButton.setEnabled(false);
    }
  }

  private void changeVisibleState(boolean suspendThreadSupported) {
    mySuspendAll.setVisible(suspendThreadSupported);
    mySuspendThread.setVisible(suspendThreadSupported);
    myMakeDefaultPanel.setVisible(suspendThreadSupported);
    if (!suspendThreadSupported) {
      mySuspendCheckBox.setText(XDebuggerBundle.message("suspend.policy.panel.suspend.execution"));
    }
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    myContentPane.setBorder(null);
    return false;
  }

  public void hide() {
    myContentPane.getParent().remove(myContentPane);
  }

  @Override
  void loadProperties() {
    SuspendPolicy suspendPolicy = myBreakpoint.getSuspendPolicy();
    boolean selected = suspendPolicy != SuspendPolicy.NONE;
    boolean suspendThreadSupported = myBreakpoint.getType().isSuspendThreadSupported();

    changeVisibleState(suspendThreadSupported);
    if (suspendThreadSupported) {
      // Preselect default policy if the current policy is "suspend none".
      var adjustedPolicy = (suspendPolicy != SuspendPolicy.NONE) ? suspendPolicy : getDefaultSuspendPolicy();
      mySuspendPolicyGroup.setSelected(adjustedPolicy == SuspendPolicy.THREAD ? mySuspendThread.getModel() : mySuspendAll.getModel(), true);
      changeEnableState(selected);
    }

    mySuspendCheckBox.setSelected(selected);
    showActionOptionsIfNeeded(selected);
  }

  private SuspendPolicy getSelectedSuspendPolicy() {
    if (!mySuspendCheckBox.isSelected()) {
      return SuspendPolicy.NONE;
    }
    else if (myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendAll.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.THREAD;
    }
    else {
      return myBreakpoint.getType().getDefaultSuspendPolicy();
    }
  }

  @Override
  void saveProperties() {
    myBreakpoint.setSuspendPolicy(getSelectedSuspendPolicy());
  }

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }
}
