/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class XSuspendPolicyPanel extends XBreakpointPropertiesSubPanel {
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAll;
  private JRadioButton mySuspendThread;

  private JPanel myContentPane;
  private JButton myMakeDefaultButton;
  private JPanel myMakeDefaultPanel;

  private ButtonGroup mySuspendPolicyGroup;

  public interface Delegate {
    void showMoreOptionsIfNeeded();
  }

  private Delegate myDelegate;

  @Override
  public void init(Project project, final XBreakpointManager breakpointManager, @NotNull XBreakpointBase breakpoint) {
    super.init(project, breakpointManager, breakpoint);

    mySuspendCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        boolean selected = mySuspendCheckBox.isSelected();

        if (myBreakpoint.getType().isSuspendThreadSupported()) {
          changeEnableState(selected);
        }

        if (myDelegate != null && !selected) {
          myDelegate.showMoreOptionsIfNeeded();
        }
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
        ((XBreakpointManagerImpl)myBreakpointManager).getBreakpointDefaults(myBreakpointType).setSuspendPolicy(suspendPolicy);
        updateSuspendPolicyFont();
        if (SuspendPolicy.THREAD == suspendPolicy) {
          mySuspendThread.requestFocus();
        }
        else {
          mySuspendAll.requestFocus();
        }
        myMakeDefaultButton.setEnabled(false);
      }
    });
  }

  private void updateMakeDefaultEnableState() {
    boolean enabled = !getSelectedSuspendPolicy().equals(
      ((XBreakpointManagerImpl)myBreakpointManager).getBreakpointDefaults(myBreakpointType).getSuspendPolicy());
    ((CardLayout)myMakeDefaultPanel.getLayout()).show(myMakeDefaultPanel, enabled ? "Show" : "Hide");
    myMakeDefaultButton.setVisible(enabled);
    myMakeDefaultButton.setEnabled(enabled);
  }

  private void updateSuspendPolicyFont() {
    SuspendPolicy defaultPolicy = ((XBreakpointManagerImpl)myBreakpointManager).getBreakpointDefaults(myBreakpointType).getSuspendPolicy();
    Font font = mySuspendAll.getFont().deriveFont(Font.PLAIN);
    Font boldFont = font.deriveFont(Font.BOLD);

    mySuspendAll.setFont(SuspendPolicy.ALL.equals(defaultPolicy) ? boldFont : font);
    mySuspendThread.setFont(SuspendPolicy.THREAD.equals(defaultPolicy) ? boldFont : font);
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
      mySuspendPolicyGroup.setSelected(suspendPolicy == SuspendPolicy.THREAD ? mySuspendThread.getModel() : mySuspendAll.getModel(), true);
      changeEnableState(selected);
    }

    mySuspendCheckBox.setSelected(selected);
    if (!selected && myDelegate != null) {
      myDelegate.showMoreOptionsIfNeeded();
    }
  }

  private SuspendPolicy getSelectedSuspendPolicy() {
    if (!mySuspendCheckBox.isSelected()) {
      return SuspendPolicy.NONE;
    }
    else if (myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendAll.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.THREAD;
    }
    else {
      return SuspendPolicy.ALL;
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
