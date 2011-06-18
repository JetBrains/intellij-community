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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: zajac
 * Date: 16.06.11
 * Time: 17:40
 * To change this template use File | Settings | File Templates.
 */
public class XSuspendPolicyPanel<B extends XBreakpoint<?>> extends XBreakpointPropertiesSubPanel<B> {

  private JPanel mySuspendPolicyPanel;
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAllRadioButton;
  private JRadioButton mySuspendThreadRadioButton;
  private JRadioButton mySuspendNoneRadioButton;
  private JPanel myContentPane;
  private Map<SuspendPolicy, JRadioButton> mySuspendRadioButtons;

  public void init(Project project, final XBreakpointManager breakpointManager, @NotNull B breakpoint) {
    super.init(project, breakpointManager, breakpoint);
    mySuspendRadioButtons = new HashMap<SuspendPolicy, JRadioButton>();
    mySuspendRadioButtons.put(SuspendPolicy.ALL, mySuspendAllRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.THREAD, mySuspendThreadRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.NONE, mySuspendNoneRadioButton);
    @NonNls String card = myBreakpointType.isSuspendThreadSupported() ? "radioButtons" : "checkbox";
    ((CardLayout)mySuspendPolicyPanel.getLayout()).show(mySuspendPolicyPanel, card);
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    mySuspendPolicyPanel.setBorder(null);
    return false;
  }

  @Override
  void loadProperties() {
    SuspendPolicy suspendPolicy = myBreakpoint.getSuspendPolicy();
    mySuspendRadioButtons.get(suspendPolicy).setSelected(true);
    mySuspendCheckBox.setSelected(suspendPolicy != SuspendPolicy.NONE);
  }

  private SuspendPolicy getConfiguredSuspendPolicy() {
    if (!myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendCheckBox.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.NONE;
    }

    for (SuspendPolicy policy : mySuspendRadioButtons.keySet()) {
      if (mySuspendRadioButtons.get(policy).isSelected()) {
        return policy;
      }
    }
    return SuspendPolicy.ALL;
  }


  @Override
  void saveProperties() {
    myBreakpoint.setSuspendPolicy(getConfiguredSuspendPolicy());
  }
}
