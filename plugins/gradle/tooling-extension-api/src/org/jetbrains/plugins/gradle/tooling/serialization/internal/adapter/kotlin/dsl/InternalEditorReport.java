// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.kotlin.dsl;

import org.gradle.tooling.model.kotlin.dsl.EditorReport;
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity;

import java.io.Serializable;

public class InternalEditorReport implements EditorReport, Serializable {

  private final EditorReportSeverity mySeverity;
  private final String myMessage;
  private final InternalEditorPosition myEditorPosition;

  public InternalEditorReport(EditorReportSeverity severity,
                              String message,
                              InternalEditorPosition position) {
    mySeverity = severity;
    myMessage = message;
    myEditorPosition = position;
  }

  @Override
  public EditorReportSeverity getSeverity() {
    return mySeverity;
  }

  @Override
  public String getMessage() {
    return myMessage;
  }

  @Override
  public InternalEditorPosition getPosition() {
    return myEditorPosition;
  }
}
