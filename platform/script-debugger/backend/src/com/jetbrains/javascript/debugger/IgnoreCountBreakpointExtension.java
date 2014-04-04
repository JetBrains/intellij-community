// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;

/**
 * An extension to breakpoint API that supports 'ignore count' property. Some backends
 * or VM versions may not support it.
 * @see com.jetbrains.javascript.debugger.Vm#getIgnoreCountBreakpointExtension()
 * @see Breakpoint#getIgnoreCountBreakpointExtension()
 */
public interface IgnoreCountBreakpointExtension {
  /**
   * This value is used when the corresponding parameter is absent.
   *
   * @see #setIgnoreCount
   */
  int EMPTY_VALUE = Breakpoint.EMPTY_VALUE;

  /**
   * Sets a breakpoint with the specified parameters.
   * @param target of the breakpoint
   * @param line in the script or function (1-based). If none, use
   *        {@link Breakpoint#EMPTY_VALUE}
   * @param column of the target start within the line (1-based). If none, use
   *        {@link Breakpoint#EMPTY_VALUE}
   * @param enabled whether the breakpoint is enabled initially
   * @param ignoreCount number specifying the amount of breakpoint hits to
   *        ignore. If none, use {@link #EMPTY_VALUE}
   * @param condition nullable string with breakpoint condition
   * @param callback to invoke when the evaluation result is ready,
   *        may be {@code null}
   */
  AsyncResult<Breakpoint> setBreakpoint(Vm vm, BreakpointTarget target, int line, int column, boolean enabled, String condition, int ignoreCount);

  /**
   * Sets the ignore count for this breakpoint ({@code EMPTY_VALUE} to clear).
   * Does <strong>not</strong> require subsequent flush call.
   * @param ignoreCount the new ignored hits count to set
   */
  ActionCallback setIgnoreCount(Breakpoint breakpoint, int ignoreCount);
}