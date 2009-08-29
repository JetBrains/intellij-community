package com.intellij.xdebugger;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebugProcessStarter {

  @NotNull
  public abstract XDebugProcess start(@NotNull XDebugSession session);

}
