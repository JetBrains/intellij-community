// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

public class NopProcessHandler extends ProcessHandler {
  @Override
  protected void destroyProcessImpl() {
    notifyProcessTerminated(0);
  }

  @Override
  protected void detachProcessImpl() {
    notifyProcessDetached();
  }

  @Override
  public boolean detachIsDefault() {
    return false;
  }

  @Override
  public @Nullable OutputStream getProcessInput() {
    return null;
  }
}
