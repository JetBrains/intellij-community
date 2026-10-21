// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Implemented by an [XBreakpointProperties] of a data breakpoint (a.k.a. watchpoint) so that the
 * generic breakpoint editor can display and edit it without a type-specific extension point — the
 * same mechanism [XLineBreakpoint] uses to expose its own fields generically.
 */
@ApiStatus.Experimental
interface XDataBreakpointProperties {
  val expressionPresentation: @NlsSafe String?
  val sizeBytes: Int?
  val registrationError: @NlsSafe String?
  // Prefixed with "data" (unlike the other members here) so an implementer that already has its
  // own same-named, differently-typed access-type/lifetime/hit-count-mode fields (e.g. CIDR's
  // watchpoint properties) can implement this interface without a Kotlin member name collision.
  var dataAccessType: XDataBreakpointAccessType?
  var dataLifetime: XDataBreakpointLifetime?
  var hitCountEnabled: Boolean
  var dataHitCountMode: XDataBreakpointHitCountMode
  var hitCountValue: Int
}

@ApiStatus.Experimental
@Serializable
enum class XDataBreakpointAccessType {
  READ, WRITE, ANY
}

@ApiStatus.Experimental
@Serializable
enum class XDataBreakpointLifetime {
  STACK_FRAME, PERSISTENT
}

@ApiStatus.Experimental
@Serializable
enum class XDataBreakpointHitCountMode {
  EQUAL_TO, GREATER_THAN_OR_EQUAL_TO, MULTIPLE_OF
}
