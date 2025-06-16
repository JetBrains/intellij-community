// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.config.AntBuildFile;

public final class AntFinishedExecutionEvent extends AntExecutionEvent {
  private final Status myStatus;
  private final int myErrorCount;

  public enum Status {
    SUCCESS, CANCELED, FAILURE
  }

  AntFinishedExecutionEvent(AntBuildFile buildFile, Status status, int errorCount) {
    super(buildFile);
    myStatus = status;
    myErrorCount = errorCount;
  }

  public Status getStatus() {
    return myStatus;
  }

  public int getErrorCount() {
    return myErrorCount;
  }
}
