// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compilation.charts.jps;

import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;

import static com.intellij.compilation.charts.jps.ChartsBuilderService.COMPILATION_STATUS_BUILDER_ID;

public class CompilationStatusBuilderMessage extends CustomBuilderMessage {
  public CompilationStatusBuilderMessage(String messageType) {
    super(COMPILATION_STATUS_BUILDER_ID, messageType, String.valueOf(System.nanoTime()));
  }
}
