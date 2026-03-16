// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.annotations.ApiStatus

private val logger = logger<XBreakpointBehaviorPolicy>()

/**
 * Defines a policy to handle breakpoint errors during a debugging session.
 *
 * This interface allows the customization of behavior when a breakpoint error occurs,
 * enabling extension points to determine the appropriate action to take.
 *
 * Currently supported only for JVM debugger.
 */
@ApiStatus.Experimental
interface XBreakpointBehaviorPolicy {
  companion object {
    private val EP_NAME: ExtensionPointName<XBreakpointBehaviorPolicy> =
      ExtensionPointName.create("com.intellij.xdebugger.breakpointBehaviorPolicy")

    /**
     * Determines the appropriate action to take for a breakpoint error in a debugging session.
     *
     * This method evaluates all available breakpoint behavior policies and applies the first
     * non-default action provided. If no policy-specific action is defined, a default action
     * of `UNHANDLED` is returned.
     *
     * @param session the debugging session in which the breakpoint error occurred
     * @param breakpoint the specific breakpoint associated with the error
     * @param errorData the error details, including its title, message, and optional cause
     * @return the chosen action to handle the breakpoint error, defaults to `UNHANDLED` if no policies apply
     */
    @JvmStatic
    fun doChooseBreakpointErrorAction(
      session: XDebugSession,
      breakpoint: XBreakpoint<*>,
      errorData: BreakpointErrorData,
    ): BreakpointErrorAction {
      for (policy in EP_NAME.extensionList) {
        try {
          val action = policy.chooseBreakpointErrorAction(session, breakpoint, errorData)
          if (action != BreakpointErrorAction.UNHANDLED) {
            return action
          }
        }
        catch (t: Throwable) {
          logger.error("Failed to evaluate breakpoint behavior policy: " + policy.javaClass.getName(), t)
        }
      }
      return BreakpointErrorAction.UNHANDLED
    }
  }

  enum class BreakpointErrorAction {
    UNHANDLED,
    PAUSE,
    RESUME
  }

  /**
   * Determines the appropriate action to take for a breakpoint error in a debugging session,
   * e.g. when a conditional expression is incorrect.
   *
   * An implementation may choose to pause the session, resume execution, or keep the default behavior.
   *
   * If all such methods return [BreakpointErrorAction.UNHANDLED] in the EP chain, the dialog will be shown to a user.
   *
   * For any unrelared breakpoints [BreakpointErrorAction.UNHANDLED] must be returned.
   */
  fun chooseBreakpointErrorAction(
    session: XDebugSession,
    breakpoint: XBreakpoint<*>,
    error: BreakpointErrorData,
  ): BreakpointErrorAction
}
