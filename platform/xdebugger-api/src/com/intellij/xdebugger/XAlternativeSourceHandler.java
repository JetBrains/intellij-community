// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows an {@link XDebugProcess} to provide an alternative view
 * of the execution position, like a disassembly view alongside regular source code.
 *
 * @see XDebugProcess#getAlternativeSourceHandler()
 */
@ApiStatus.Experimental
public interface XAlternativeSourceHandler {
  @NotNull StateFlow<@NotNull Boolean> getAlternativeSourceKindState();

  /**
   * Upon reaching a position, the platform asks the handler to provide a preferred source kind to navigate to.
   */
  boolean isAlternativeSourceKindPreferred(@NotNull XSuspendContext suspendContext);

  @Nullable XSourcePosition getAlternativePosition(@NotNull XStackFrame frame);
}
