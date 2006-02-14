/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.siyeh.ig.InspectionGadgetsPlugin;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;

public class TelemetryToolWindowImpl implements TelemetryToolWindow{

    private final TelemetryDisplay telemetryDisplay;
    private final JPanel myContentPanel;
    private ToolWindow myToolWindow = null;

    public TelemetryToolWindowImpl() {
        super();
        final Application application = ApplicationManager.getApplication();
        final InspectionGadgetsPlugin plugin =
                application.getComponent(InspectionGadgetsPlugin.class);
        final InspectionGadgetsTelemetry telemetry = plugin.getTelemetry();
        telemetryDisplay = new TelemetryDisplayImpl(telemetry);
        final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(new UpdateTelemetryViewAction(telemetryDisplay));
        toolbarGroup.add(new ResetTelemetryAction(telemetry, telemetryDisplay));
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionToolbar toolbar =
                actionManager.createActionToolbar(TOOL_WINDOW_ID,
                                                  toolbarGroup, true);
        myContentPanel = new JPanel(new BorderLayout());
        myContentPanel.setBackground(Color.gray);
        final JComponent toolbarComponent = toolbar.getComponent();
        myContentPanel.add(toolbarComponent, BorderLayout.NORTH);
        final JComponent displayContentPane = telemetryDisplay.getContentPane();
        myContentPanel.add(displayContentPane, BorderLayout.CENTER);
    }

    public void register(Project project){
        final ToolWindowManager toolWindowManager =
                ToolWindowManager.getInstance(project);
        myToolWindow =
                toolWindowManager.registerToolWindow(TOOL_WINDOW_ID,
                                                     myContentPanel,
                                                     ToolWindowAnchor.LEFT);
        myToolWindow.setTitle(InspectionGadgetsBundle.message(
                "telemetry.toolwindow.title"));
        myToolWindow.setAvailable(true, null);
    }

    public void show(){
        myToolWindow.setAvailable(true, null);
        telemetryDisplay.update();
        myToolWindow.show(null);
    }

    public void close(){
        myToolWindow.hide(null);
        myToolWindow.setAvailable(false, null);
    }

    public void unregister(Project project){
        final ToolWindowManager toolWindowManager =
                ToolWindowManager.getInstance(project);
        toolWindowManager.unregisterToolWindow(TOOL_WINDOW_ID);
    }
}