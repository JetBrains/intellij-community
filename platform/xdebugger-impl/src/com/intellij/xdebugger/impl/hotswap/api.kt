// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Provides platform-specific implementation for the hot swap process.
 *
 * The provider should be passed to [HotSwapSessionManager.createSession] on the start of a session where hot swapping is possible.
 */
@ApiStatus.Internal
interface HotSwapProvider<T> {
  /**
   * Provides notifications on the file modifications during the session.
   *
   * @param session session to track changes
   * @param coroutineScope scope that is tied to the session
   * @param listener listener to pass the updates
   * @see SourceFileChangesCollectorImpl
   */
  fun createChangesCollector(
    session: HotSwapSession<T>,
    coroutineScope: CoroutineScope,
    listener: SourceFileChangesListener,
  ): SourceFileChangesCollector<T>

  /**
   * Implementation of the hot swap process.
   * This function must call [com.intellij.xdebugger.impl.hotswap.HotSwapSession.startHotSwapListening]
   * to get a callback and use it to report the hot swap status.
   */
  fun performHotSwap(context: DataContext, session: HotSwapSession<T>)
}

/**
 * Listener to report the hot swap status.
 * @see HotSwapSession.startHotSwapListening
 * @see HotSwapStatusNotificationManager.trackNotification
 */
@ApiStatus.Internal
interface HotSwapResultListener {
  /**
   * Hot swap completed successfully, the notification is shown by [HotSwapSessionManager].
   */
  fun onSuccessfulReload()

  /**
   * Hot swap completed, a notification or error message is shown by a [HotSwapProvider].
   * Previous modifications are considered obsolete.
   */
  fun onFinish()

  /**
   * Hot swap was canceled, previous modifications are still actual.
   */
  fun onCanceled()
}

/**
 * Collection of the changed elements since the last hot swap.
 */
@ApiStatus.Internal
interface SourceFileChangesCollector<T> : Disposable {
  fun getChanges(): Set<T>
  fun resetChanges()
}

/**
 * Provides events on the source code changes.
 */
@ApiStatus.Internal
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
