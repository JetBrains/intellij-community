// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.kotlin.dsl;

import org.gradle.tooling.model.kotlin.dsl.EditorPosition;
import org.gradle.tooling.model.kotlin.dsl.EditorReport;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class InternalKotlinDslScriptModel implements KotlinDslScriptModel, Serializable {
  private final List<File> myClassPath;
  private final List<File> mySourcePath;
  private final List<String> myImplicitImports;
  private final List<EditorReport> myEditorReports;
  private final List<String> myExceptions;

  public InternalKotlinDslScriptModel(KotlinDslScriptModel dslScriptModel) {
    this(dslScriptModel.getClassPath(), dslScriptModel.getSourcePath(), dslScriptModel.getImplicitImports(),
         dslScriptModel.getEditorReports(), dslScriptModel.getExceptions());
  }

  public InternalKotlinDslScriptModel(List<File> classPath,
                                      List<File> sourcePath,
                                      List<String> implicitImports,
                                      List<EditorReport> editorReports,
                                      List<String> exceptions) {
    myClassPath = new ArrayList<File>(classPath);
    mySourcePath = new ArrayList<File>(sourcePath);
    myImplicitImports = new ArrayList<String>(implicitImports);
    myEditorReports = new ArrayList<EditorReport>(editorReports.size());
    for (EditorReport report : editorReports) {
      EditorPosition position = report.getPosition();
      InternalEditorPosition internalEditorPosition =
        position == null ? null : new InternalEditorPosition(position.getLine(), position.getColumn());
      myEditorReports.add(new InternalEditorReport(report.getSeverity(), report.getMessage(), internalEditorPosition));
    }
    myExceptions = new ArrayList<String>(exceptions);
  }

  @Override
  public List<File> getClassPath() {
    return myClassPath;
  }

  @Override
  public List<File> getSourcePath() {
    return mySourcePath;
  }

  @Override
  public List<String> getImplicitImports() {
    return myImplicitImports;
  }

  @Override
  public List<EditorReport> getEditorReports() {
    return myEditorReports;
  }

  @Override
  public List<String> getExceptions() {
    return myExceptions;
  }
}
