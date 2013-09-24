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
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class XSuspendPolicyPanel<B extends XBreakpoint<?>> extends XBreakpointPropertiesSubPanel<B> {
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAllRadioButton;
  private JRadioButton mySuspendThreadRadioButton;

  private JPanel myContentPane;

  public interface Delegate {
    void showMoreOptionsIfNeeded();
  }

  private Delegate myDelegate;

  @Override
  public void init(Project project, final XBreakpointManager breakpointManager, @NotNull B breakpoint) {
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
  }

  private void changeEnableState(boolean selected) {
    mySuspendAllRadioButton.setEnabled(selected);
    mySuspendThreadRadioButton.setEnabled(selected);
  }

  private void changeVisibleState(boolean suspendThreadSupported) {
    mySuspendAllRadioButton.setVisible(suspendThreadSupported);
    mySuspendThreadRadioButton.setVisible(suspendThreadSupported);
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    myContentPane.setBorder(null);
    return false;
  }

  @Override
  void loadProperties() {
    SuspendPolicy suspendPolicy = myBreakpoint.getSuspendPolicy();
    boolean selected = suspendPolicy != SuspendPolicy.NONE;
    boolean suspendThreadSupported = myBreakpoint.getType().isSuspendThreadSupported();

    changeVisibleState(suspendThreadSupported);
    if (suspendThreadSupported) {
      mySuspendAllRadioButton.setSelected(suspendPolicy == SuspendPolicy.ALL);
      mySuspendThreadRadioButton.setSelected(suspendPolicy == SuspendPolicy.THREAD);
      changeEnableState(selected);
    }

    mySuspendCheckBox.setSelected(selected);
    if (!selected && myDelegate != null) {
      myDelegate.showMoreOptionsIfNeeded();
    }
  }

  private SuspendPolicy getConfiguredSuspendPolicy() {
    if (!mySuspendCheckBox.isSelected()) {
      return SuspendPolicy.NONE;
    }
    else if (myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendAllRadioButton.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.THREAD;
    }
    else {
      return SuspendPolicy.ALL;
    }
  }

  @Override
  void saveProperties() {
    myBreakpoint.setSuspendPolicy(getConfiguredSuspendPolicy());
  }

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }
}
