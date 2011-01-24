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
package com.siyeh.ig;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.telemetry.TelemetryToolWindow;
import org.jetbrains.annotations.NotNull;

public class InspectionGadgetsProjectComponent implements ProjectComponent{

    private TelemetryToolWindow toolWindow = null;
    private boolean telemetryEnabled = true;
    private final Project project;

    public InspectionGadgetsProjectComponent(Project project){
        this.project = project;
    }

    public void projectOpened(){
        final Application application = ApplicationManager.getApplication();
        final InspectionGadgetsPlugin inspectionGadgetsPlugin =
                (InspectionGadgetsPlugin)
                        application.getComponent("InspectionGadgets");
        telemetryEnabled = inspectionGadgetsPlugin.isTelemetryEnabled();
        if(telemetryEnabled){
            toolWindow = new TelemetryToolWindow(inspectionGadgetsPlugin.getTelemetry());
            toolWindow.register(project);
        }
    }

    public void projectClosed(){
        if(telemetryEnabled && toolWindow != null){
            TelemetryToolWindow.unregister(project);
        }
    }

    @NotNull
    public String getComponentName(){
        return "InspectionGadgetsProjectComponent";
    }

    public void initComponent(){
    }

    public void disposeComponent(){
    }
}
