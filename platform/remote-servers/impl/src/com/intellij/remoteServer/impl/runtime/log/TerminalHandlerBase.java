// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class TerminalHandlerBase extends LoggingHandlerBase implements TerminalHandler {

  private boolean myClosed = false;

  public TerminalHandlerBase(@NotNull String presentableName) {
    super(presentableName);
  }

  @Override
  public abstract JComponent getComponent();

  @Override
  public boolean isClosed() {
    return myClosed;
  }

  @Override
  public void close() {
    myClosed = true;
  }
}
