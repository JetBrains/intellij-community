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
package com.siyeh.ig;

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
        telemetryEnabled = InspectionGadgetsPlugin.isTelemetryEnabled();
        if(telemetryEnabled){
            toolWindow = new TelemetryToolWindowImpl();
            toolWindow.register(project);
        }
    }

    public void projectClosed(){
        if(telemetryEnabled && toolWindow != null){
            toolWindow.unregister(project);
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
