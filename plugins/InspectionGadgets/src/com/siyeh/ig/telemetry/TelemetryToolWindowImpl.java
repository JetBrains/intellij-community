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

import javax.swing.*;
import java.awt.*;

public class TelemetryToolWindowImpl implements TelemetryToolWindow{
    private TelemetryDisplay telemetryDisplay;
    private JPanel myContentPanel;
    private ToolWindow myToolWindow = null;
    private Project project;

    public TelemetryToolWindowImpl(Project project){
        super();
        this.project = project;
        final Application application = ApplicationManager.getApplication();
        final InspectionGadgetsPlugin plugin =
                (InspectionGadgetsPlugin) application.getComponent("InspectionGadgets");
        final InspectionGadgetsTelemetry telemetry = plugin.getTelemetry();
        telemetryDisplay = new TelemetryDisplayImpl(telemetry);
        final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(new UpdateTelemetryViewAction(telemetryDisplay));
        toolbarGroup.add(new ResetTelemetryAction(telemetry, telemetryDisplay));
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionToolbar toolbar =
                actionManager.createActionToolbar(CYCLE_TOOL_WINDOW_ID,
                                                  toolbarGroup, true);
        myContentPanel = new JPanel(new BorderLayout());
        myContentPanel.setBackground(Color.gray);
        final JComponent toolbarComponent = toolbar.getComponent();
        myContentPanel.add(toolbarComponent, BorderLayout.NORTH);
        final JComponent displayContentPane = telemetryDisplay.getContentPane();
        myContentPanel.add(displayContentPane, BorderLayout.CENTER);
    }

    public void register(){
        final ToolWindowManager toolWindowManager =
                ToolWindowManager.getInstance(project);
        myToolWindow =
                toolWindowManager.registerToolWindow(CYCLE_TOOL_WINDOW_ID,
                                                     myContentPanel,
                                                     ToolWindowAnchor.BOTTOM);
        myToolWindow.setTitle("IG Telemetry");
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


    public void unregister(){
        final ToolWindowManager toolWindowManager =
                ToolWindowManager.getInstance(project);
        toolWindowManager.unregisterToolWindow(CYCLE_TOOL_WINDOW_ID);
    }
}
