// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.hint.ParameterInfoControllerBase
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import junit.framework.TestCase.fail
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Helpers for testing AutoPopup Parameter Info
 */
object AutoPopupParameterInfoTestUtil {
  @JvmStatic
  fun waitForParameterInfoUpdate(editor: Editor) {
    try {
      ParameterInfoControllerBase.waitForDelayedActions(editor, 1, TimeUnit.MINUTES)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
    catch (e: TimeoutException) {
      fail("Timed out waiting for parameter info update")
    }
  }

  @JvmStatic
  fun waitForAutoPopup(project: Project) {
    try {
      AutoPopupController.getInstance(project).waitForDelayedActions(1, TimeUnit.MINUTES)
    }
    catch (e: TimeoutException) {
      fail("Timed out waiting for auto-popup")
    }
  }
}
