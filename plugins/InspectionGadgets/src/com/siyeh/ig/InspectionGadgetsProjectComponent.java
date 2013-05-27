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

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.siyeh.ig.telemetry.InspectionGadgetsTelemetry;
import com.siyeh.ig.telemetry.TelemetryToolWindow;
import org.jetbrains.annotations.NotNull;

public class InspectionGadgetsProjectComponent implements ProjectComponent {
  private final Project project;

  public InspectionGadgetsProjectComponent(Project project) {
    this.project = project;
  }

  @Override
  public void projectOpened() {
    final InspectionGadgetsPlugin inspectionGadgetsPlugin = InspectionGadgetsPlugin.getInstance();
    final InspectionGadgetsTelemetry telemetry = inspectionGadgetsPlugin.getTelemetry();
    final boolean telemetryEnabled = InspectionGadgetsPlugin.getUpToDateTelemetryEnabled(new Consumer<Boolean>() {
      @Override
      public void consume(Boolean value) {
        final boolean telemetryEnabled = value.booleanValue();
        if (telemetryEnabled) {
          final TelemetryToolWindow toolWindow = new TelemetryToolWindow(telemetry);
          toolWindow.register(project);
        }
        else {
          TelemetryToolWindow.unregister(project);
        }
      }
    }, project);
    if (telemetryEnabled) {
      final TelemetryToolWindow toolWindow = new TelemetryToolWindow(telemetry);
      toolWindow.register(project);
    }
  }

  @Override
  public void projectClosed() {
    TelemetryToolWindow.unregister(project);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "InspectionGadgetsProjectComponent";
  }

  @Override
  public void initComponent() {}

  @Override
  public void disposeComponent() {}
}
