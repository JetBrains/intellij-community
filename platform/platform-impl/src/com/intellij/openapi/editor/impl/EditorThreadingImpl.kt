// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.isEditorLockFreeTypingEnabled
import com.intellij.openapi.editor.EditorThreading
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditorThreadingImpl : EditorThreading {

  override fun doAssertInteractionAllowed() {
    if (!EDT.isCurrentThreadEdt() && !application.isReadAccessAllowed) {
      throw IllegalStateException("Access to Editor models (caret, selection, etc.) is allowed either from EDT, or under read action. Current thread: ${Thread.currentThread()}")
    }
  }

  override fun <T, E : Throwable> doCompute(action: ThrowableComputable<T, E>): T {
    return if (EDT.isCurrentThreadEdt()) {
      action.compute()
    }
    else {
      application.runReadAction(action)
    }
  }

  override fun doRun(action: Runnable) {
    return if (EDT.isCurrentThreadEdt()) {
      action.run()
    }
    else {
      application.runReadAction(action)
    }
  }

  override fun <T, E : Throwable> doComputeWritable(action: ThrowableComputable<T, E>): T {
    return if (isLockFreeTypingAllowed()) {
      action.compute()
    }
    else {
      WriteIntentReadAction.computeThrowable(action)
    }
  }

  override fun doRunWritable(action: Runnable) {
    if (isLockFreeTypingAllowed()) {
      action.run()
    }
    else {
      WriteIntentReadAction.run(action)
    }
  }

  override fun doAssertWriteAllowed() {
    if (!(isLockFreeTypingAllowed())) {
      ThreadingAssertions.assertWriteAccess()
    }
  }

  override fun doWrite(action: Runnable) {
    if (isLockFreeTypingAllowed() && isNonModal()) {
      action.run()
    }
    else {
      application.runWriteAction(action)
    }
  }

  private fun isLockFreeTypingAllowed(): Boolean {
    return isEditorLockFreeTypingEnabled && EDT.isCurrentThreadEdt()
  }

  /**
   * Modal dialogs hold WIL on EDT preventing BGT WA to be started.
   * See usages of [com.intellij.openapi.ui.impl.AbstractDialog.show].
   * It means if an editor is inside a modal dialog (e.g., Create Branch from <branch>),
   * then the corresponding ui document can't be synced with the real one until the editor is closed,
   * which makes no sense.
   */
  private fun isNonModal(): Boolean {
    return ModalityState.current() == ModalityState.nonModal()
  }
}
