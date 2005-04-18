package com.siyeh.ig;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.telemetry.TelemetryToolWindow;
import com.siyeh.ig.telemetry.TelemetryToolWindowImpl;

public class InspectionGadgetsProjectComponent implements ProjectComponent{
    private TelemetryToolWindow toolWindow = null;
    private boolean telemetryEnabled = true;
    private Project project;

    public InspectionGadgetsProjectComponent(Project project){
        super();
        this.project = project;
    }


    public void projectOpened(){
        final Application application = ApplicationManager.getApplication();
        final InspectionGadgetsPlugin plugin =
                (InspectionGadgetsPlugin) application.getComponent("InspectionGadgets");
        telemetryEnabled = plugin.isTelemetryEnabled();
        if(telemetryEnabled){
            toolWindow = new TelemetryToolWindowImpl(project);
            toolWindow.register();
        }
    }

    public void projectClosed(){
        if(telemetryEnabled){
            toolWindow.unregister();
        }
    }

    public String getComponentName(){
        return "InspectionGadgetsProjectComponent";
    }

    public void initComponent(){
    }

    public void disposeComponent(){
    }
}
