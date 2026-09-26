// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Represents the result of a successfully started debugging session.
 *
 * This interface provides access to the associated debugging session
 * and an optional content descriptor related to the session.
 */
@ApiStatus.NonExtendable
interface XSessionStartedResult {
  val session: XDebugSession

  /**
   * This descriptor is designed to be used only for passing it as a result for
   * [com.intellij.execution.ExecutionManager.startRunProfile],
   * [com.intellij.execution.runners.AsyncProgramRunner.execute],
   * or similar methods for starting a process.
   *
   * Do not use it to access session details or manipulate the session state, use [session] directly instead.
   *
   * Example:
   * ```
   * class MyProgramRunner : AsyncProgramRunner<RunnerSettings>() {
   *   override fun execute(
   *     environment: ExecutionEnvironment,
   *     state: RunProfileState,
   *   ): Promise<RunContentDescriptor?> {
   *     val result = XDebuggerManager.getInstance(environment.project)
   *       .newSessionBuilder(processStarter)
   *       .environment(environment)
   *       .startSession()
   *     return resolvedPromise(result.runContentDescriptor)
   *   }
   * }
   * ```
   */
  val runContentDescriptor: RunContentDescriptor?
}

/**
 * Builder interface for creating and configuring a debugging session.
 *
 * By default, the session is not shown after started. In this case, [environment] is a required parameter.
 * To start a session with a tab shown, call [showTab] with `true` and pass a [sessionName].
 *
 * Example:
 * ```
 * XDebuggerManager.getInstance(project).newSessionBuilder(starter)
 *         .environment(env)
 *         .sessionName(sessionName)
 *         .icon(icon)
 *         .contentToReuse(content)
 *         .showTab(true)
 *         .showToolWindowOnSuspendOnly(false)
 *         .startSession()
 * ```
 */
@ApiStatus.NonExtendable
interface XDebugSessionBuilder {
  /**
   * Starts the debugging session using the [XDebugProcessStarter] passed to [XDebuggerManager.newSessionBuilder].
   *
   * @return the started session together with an optional run content descriptor for execution APIs.
   * @throws ExecutionException if the debug process cannot be created or initialized.
   */
  @Throws(ExecutionException::class)
  fun startSession(): XSessionStartedResult

  /**
   * Sets the execution environment associated with the session and its run configuration.
   *
   * Use this for debugger sessions started from the execution subsystem, for example from
   * [com.intellij.execution.runners.ProgramRunner] or [com.intellij.execution.runners.AsyncProgramRunner]. The environment
   * provides the project, run profile, executor, execution id, and the content descriptor that may be reused on restart.
   *
   * This parameter is required when [showTab] is left `false` (the default): in that mode the debugger creates session
   * content for the execution subsystem, but does not show the tab itself.
   */
  fun environment(environment: ExecutionEnvironment): XDebugSessionBuilder

  /**
   * Sets the title used for the session tab in the Debug tool window.
   *
   * The name is required when [showTab] is set to `true`.
   */
  fun sessionName(@Nls sessionName: @Nls String): XDebugSessionBuilder

  /**
   * Sets the icon used for the session tab in the Debug tool window.
   *
   * No explicit icon is set by default. If `null`, the default session icon is used.
   */
  fun icon(icon: Icon?): XDebugSessionBuilder

  /**
   * Sets an existing run content descriptor whose Debug tool window tab should be reused for the new session.
   *
   * The descriptor normally comes from [ExecutionEnvironment.getContentToReuse]. The execution subsystem fills that value
   * when a run configuration is restarted or when the user explicitly starts debugging in an existing run content tab.
   *
   * Pass it explicitly when [showTab] is `true` or when the session is not started through a standard
   * [ExecutionEnvironment]. This lets the debugger replace or reuse the existing tab, keep its activation/selection
   * behavior, and restore debugger session data associated with that tab.
   *
   * No explicit descriptor is set by default. When [showTab] is left `false`, the descriptor from [environment] is used.
   */
  fun contentToReuse(contentToReuse: RunContentDescriptor?): XDebugSessionBuilder

  /**
   * Controls whether the session tab is created and shown by the debugger infrastructure.
   *
   * The default value is `false`. When set to `true`, [sessionName] must be provided.
   * When set to `false`, [environment] must be provided.
   */
  fun showTab(value: Boolean): XDebugSessionBuilder

  /**
   * Controls whether the Debug tool window should stay hidden until the session is suspended on a breakpoint.
   *
   * The default value is `false`. This option is used only when [showTab] is set to `true`.
   */
  fun showToolWindowOnSuspendOnly(value: Boolean): XDebugSessionBuilder
}
