// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import org.jetbrains.annotations.ApiStatus

/**
 * Marks a line breakpoint type whose breakpoint stops where the declaration at its line is used, not at the line itself.
 *
 * A field watchpoint and a collection watchpoint are such types.
 * A client that asks for a stop at a source line, such as a DAP `setBreakpoints` request, must not create one.
 */
@ApiStatus.Experimental
interface XWatchpointType
