// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.export;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

@State(name = "ExportTestResults", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
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
  public void loadState(@NotNull State state) {
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
