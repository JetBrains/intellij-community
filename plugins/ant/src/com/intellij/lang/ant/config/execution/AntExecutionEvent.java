// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.config.AntBuildFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class AntExecutionEvent {

  private final AntBuildFile myBuildFile;

  protected AntExecutionEvent(AntBuildFile buildFile) {
    myBuildFile = buildFile;
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }
}
