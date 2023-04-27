// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XSourcePosition;
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
  @Nullable XSourcePosition getAlternativePosition(@NotNull XStackFrame frame);
}
