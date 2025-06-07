// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a session of a hot swappable process, e.g., a debug session.
 *
 * Create this by calling [HotSwapSessionManager.createSession].
 */
@ApiStatus.NonExtendable
interface HotSwapSession<T> {
  val project: Project

  /**
   * Get elements modified since the last hot swap.
   */
  fun getChanges(): Set<T>

  /**
   * Start a hot swap process.
   * @return a callback to report the hot swap status
   */
  fun startHotSwapListening(): HotSwapResultListener
}

/**
 * Manages the hot swap sessions lifetime, including switching between sessions.
 */
@ApiStatus.NonExtendable
interface HotSwapSessionManager {
  /**
   * Start a hot swap session and source file tracking.
   *
   * @param provider platform-specific provider of hot swap
   * @param disposable handles the session end
   */
  fun <T> createSession(provider: HotSwapProvider<T>, disposable: Disposable): HotSwapSession<T>

  /**
   * Notify about session selection changes, e.g., switching between two debugger sessions.
   */
  fun onSessionSelected(session: HotSwapSession<*>)

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapSessionManager = project.service()
  }
}

/**
 * Provides platform-specific implementation for the hot swap process.
 *
 * The provider should be passed to [HotSwapSessionManager.createSession] on the start of a session where hot swapping is possible.
 *
 * @see [HotSwapInDebugSessionEnabler]
 */
interface HotSwapProvider<T> {
  /**
   * Provides notifications on the file modifications during the session.
   *
   * @param session session to track changes
   * @param coroutineScope scope that is tied to the session
   * @param listener listener to pass the updates
   */
  fun createChangesCollector(
    session: HotSwapSession<T>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener,
  ): SourceFileChangesCollector<T>

  /**
   * Implementation of the hot swap process.
   * This function must call [HotSwapSession.startHotSwapListening]
   * to get a callback and use it to report the hot swap status.
   */
  fun performHotSwap(session: HotSwapSession<T>)
}

/**
 * Implement this to enable hotswap in debug sessions.
 */
@ApiStatus.Experimental
interface HotSwapInDebugSessionEnabler {
  fun createProvider(process: XDebugProcess): HotSwapProvider<*>?

  @ApiStatus.Internal
  companion object {
    private val EP_NAME = ExtensionPointName<HotSwapInDebugSessionEnabler>("com.intellij.xdebugger.hotSwapInDebugSessionEnabler")

    fun createProviderForProcess(process: XDebugProcess): HotSwapProvider<*>? {
      return EP_NAME.computeSafeIfAny { it.createProvider(process) }
    }
  }
}

/**
 * Listener to report the hot swap status.
 * @see HotSwapSession.startHotSwapListening
 */
interface HotSwapResultListener {
  /**
   * Hot swap completed successfully, the notification is shown by [HotSwapSessionManager].
   */
  fun onSuccessfulReload()

  /**
   * Hot swap completed with no result, a notification or error message is shown by a [HotSwapProvider].
   * Previous modifications are considered obsolete.
   */
  fun onFinish()

  /**
   * Hot swap failed (compilation error or hot swap is not possible), an error message is shown by a [HotSwapProvider].
   * Previous modifications are considered active.
   */
  fun onFailure()

  /**
   * Hot swap was canceled, previous modifications are still actual.
   */
  fun onCanceled()
}

/**
 * Collection of the changed elements since the last hot swap.
 */
interface SourceFileChangesCollector<T> : Disposable {
  fun getChanges(): Set<T>
  fun resetChanges()
}

/**
 * Provides events on the source code changes.
 */
interface SourceFileChangesListener {
  /**
   * Changes detected since the last reset.
   */
  fun onNewChanges()

  /**
   * Modified files were reverted to the original state, so no changes currently available.
   */
  fun onChangesCanceled()
}
