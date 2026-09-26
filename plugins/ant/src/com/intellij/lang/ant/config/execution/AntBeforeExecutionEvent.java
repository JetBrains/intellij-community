// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.lang.ant.config.AntBuildFile;

public final class AntBeforeExecutionEvent extends AntExecutionEvent{
  private final AntBuildMessageView myMessageView;

  AntBeforeExecutionEvent(AntBuildFile buildFile, AntBuildMessageView messageView) {
    super(buildFile);
    myMessageView = messageView;
  }

  public AntBuildMessageView getMessageView() {
    return myMessageView;
  }
}
