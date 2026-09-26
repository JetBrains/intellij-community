// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h3>API for integration with debuggers of custom languages/frameworks</h3>
 * <p>
 * {@link com.intellij.xdebugger.XDebugProcess} is the main class which need to be extended to support debugging.
 * <p>
 * Implement {@link com.intellij.xdebugger.breakpoints.XLineBreakpointType} to support new type of breakpoints.
 * <p>
 * Use {@link com.intellij.xdebugger.settings.XDebuggerSettings} to provide settings.
 */
package com.intellij.xdebugger;