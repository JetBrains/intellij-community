package com.siyeh.ig;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.telemetry.TelemetryToolWindowImpl;
import com.siyeh.ig.telemetry.TelemetryToolWindow;

public class InspectionGadgetsProjectComponent implements ProjectComponent{
    private TelemetryToolWindow toolWindow = null;

    public InspectionGadgetsProjectComponent(Project project){
        super();
        this.project = project;
    }

    private Project project;

    public void projectOpened(){
        toolWindow = new TelemetryToolWindowImpl(project);
        toolWindow.register();
    }

    public void projectClosed(){
        toolWindow.unregister();
    }

    public String getComponentName(){
        return "InspectionGadgetsProjectComponent";
    }

    public void initComponent(){
    }

    public void disposeComponent(){
    }
}
