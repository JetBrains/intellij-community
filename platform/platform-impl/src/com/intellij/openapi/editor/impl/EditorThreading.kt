// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus

/**
 * Historically, Editor models required read lock for reading them.
 * We are planning to relax the contracts and allow interacting with Editor from EDT without any locks.
 * Hence, editor models should use this class to ensure consistency.
 */
@ApiStatus.Experimental
object EditorThreading {

  /**
   * Checks that interaction with the editor is allowed in the current context
   */
  @JvmStatic
  fun assertInteractionAllowed() {
    if (Registry.`is`("editor.allow.raw.access.on.edt")) {
      if (!EDT.isCurrentThreadEdt() && !application.isReadAccessAllowed) {
        throw IllegalStateException("Access to Editor models (caret, selection, etc.) is allowed either from EDT, or under read action. Current thread: ${Thread.currentThread()}")
      }
    }
    else {
      ThreadingAssertions.assertReadAccess()
    }
  }

  /**
   * Adjust the context for editor accessing operation [action].
   * [action] is always executed in place.
   */
  @JvmStatic
  fun <T, E : Throwable> compute(action: ThrowableComputable<T, E>): T {
    return if (Registry.`is`("editor.allow.raw.access.on.edt")) {
      if (EDT.isCurrentThreadEdt()) {
        action.compute()
      }
      else {
        application.runReadAction(action)
      }
    }
    else {
      application.runReadAction(action)
    }
  }

  /**
   * Adjust the context for editor accessing operation [action].
   * [action] is always executed in place.
   */
  @JvmStatic
  fun <T, E : Throwable> run(action: Runnable) {
    return if (Registry.`is`("editor.allow.raw.access.on.edt")) {
      if (EDT.isCurrentThreadEdt()) {
        action.run()
      }
      else {
        application.runReadAction(action)
      }
    }
    else {
      application.runReadAction(action)
    }
  }
}
