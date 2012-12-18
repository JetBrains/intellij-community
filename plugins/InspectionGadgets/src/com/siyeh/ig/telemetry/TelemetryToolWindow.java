/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.telemetry;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class TelemetryToolWindow {

  @NonNls
  private static final String TOOL_WINDOW_ID = "IG Telemetry";

  private final JPanel contentPanel;

  public TelemetryToolWindow(InspectionGadgetsTelemetry telemetry) {
    final TelemetryDisplay telemetryDisplay =
      new TelemetryDisplay(telemetry);
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(
      new UpdateTelemetryViewAction(telemetry, telemetryDisplay));
    toolbarGroup.add(new ResetTelemetryAction(telemetry, telemetryDisplay));
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionToolbar toolbar =
      actionManager.createActionToolbar(TOOL_WINDOW_ID,
                                        toolbarGroup, true);
    contentPanel = new JPanel(new BorderLayout());
    contentPanel.setBackground(JBColor.GRAY);
    final JComponent toolbarComponent = toolbar.getComponent();
    contentPanel.add(toolbarComponent, BorderLayout.NORTH);
    final JComponent displayContentPane = telemetryDisplay.getContentPane();
    contentPanel.add(displayContentPane, BorderLayout.CENTER);
  }

  public void register(Project project) {
    final ToolWindowManager toolWindowManager =
      ToolWindowManager.getInstance(project);
    final ToolWindow toolWindow =
      toolWindowManager.registerToolWindow(TOOL_WINDOW_ID, false,
                                           ToolWindowAnchor.LEFT);
    toolWindow.setTitle(InspectionGadgetsBundle.message(
      "telemetry.toolwindow.title"));
    final ContentManager contentManager = toolWindow.getContentManager();
    final ContentFactory contentFactory = contentManager.getFactory();
    final Content content = contentFactory.createContent(contentPanel,
                                                         "", true);
    contentManager.addContent(content);
    toolWindow.setAvailable(true, null);
  }

  public static void unregister(Project project) {
    final ToolWindowManager toolWindowManager =
      ToolWindowManager.getInstance(project);
    toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID);
  }
}