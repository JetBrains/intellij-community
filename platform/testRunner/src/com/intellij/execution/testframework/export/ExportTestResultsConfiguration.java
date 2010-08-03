/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.testframework.export;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;

@State(
  name = "ExportTestResults",
  storages = {@Storage(
    id = "other",
    file = "$WORKSPACE_FILE$")})
public class ExportTestResultsConfiguration implements PersistentStateComponent<ExportTestResultsConfiguration.State> {

  public enum ExportFormat {
    Xml("xml"), BundledTemplate("html"), UserTemplate("html");

    private final String myExtension;

    ExportFormat(String extension) {
      myExtension = extension;
    }

    public String getDefaultExtension() {
      return myExtension;
    }
  }

  public static class State {

    @Attribute("outputFolder")
    public String outputFolder;

    @Attribute("openResultsInEditor")
    public boolean openResultsInEditor;

    @Attribute("userTempatePath")
    public String userTemplatePath;

    private ExportFormat myExportFormat = ExportFormat.BundledTemplate;

    @Attribute("exportFormat")
    public String getExportFormat() {
      return myExportFormat.name();
    }

    public void setExportFormat(String exportFormat) {
      try {
        myExportFormat = ExportFormat.valueOf(exportFormat);
      }
      catch (IllegalArgumentException e) {
        myExportFormat = ExportFormat.BundledTemplate;
      }
    }
  }

  private State myState = new State();

  public static ExportTestResultsConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, ExportTestResultsConfiguration.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public String getOutputFolder() {
    return myState.outputFolder;
  }

  public void setOutputFolder(String outputFolder) {
    myState.outputFolder = outputFolder;
  }

  public boolean isOpenResults() {
    return myState.openResultsInEditor;
  }

  public void setOpenResults(boolean openResultsInEditor) {
    myState.openResultsInEditor = openResultsInEditor;
  }

  public ExportFormat getExportFormat() {
    return myState.myExportFormat;
  }

  public void setExportFormat(ExportFormat exportFormat) {
    myState.myExportFormat = exportFormat;
  }

  public String getUserTemplatePath() {
    return myState.userTemplatePath;
  }

  public void setUserTemplatePath(String userTemplatePath) {
    myState.userTemplatePath = userTemplatePath;
  }

}
