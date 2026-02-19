// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

public interface PtyBasedProcess {
  boolean hasPty();

  void setWindowSize(int columns, int rows);
}
