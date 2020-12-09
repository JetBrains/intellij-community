// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.remoteServer.agent.util.log.TerminalListener;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.BiConsumer;

public abstract class TerminalHandlerBase extends LoggingHandlerBase implements TerminalHandler {
  private boolean myClosed = false;
  private TerminalListener.TtyResizeHandler myResizeHandler = (width, height) -> {
  };

  private BiConsumer<String, Runnable> mySetCloseFunctionConsumer = (e, r) -> {
  };

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

  public void setCloseFunction(String event, Runnable runnable) {
    this.mySetCloseFunctionConsumer.accept(event, runnable);
  }

  protected void setCloseFunctionConsumer(BiConsumer<String, Runnable> consumer) {
    this.mySetCloseFunctionConsumer = consumer;
  }

  public void setResizeHandler(@NotNull TerminalListener.TtyResizeHandler resizeHandler) {
    myResizeHandler = resizeHandler;
  }

  @NotNull
  protected TerminalListener.TtyResizeHandler getResizeHandler() {
    return myResizeHandler;
  }
}