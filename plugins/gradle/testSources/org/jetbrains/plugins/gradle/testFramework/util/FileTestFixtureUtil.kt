// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture


fun <R> FileTestFixture.withSuppressedErrors(action: () -> R): R {
  suppressErrors(true)
  try {
    return action()
  }
  finally {
    suppressErrors(false)
  }
}

suspend fun VirtualFile.refreshAndWait() {
  writeAction {
    refresh(false, true)
  }
  blockingContext {
    invokeAndWaitIfNeeded {
      UIUtil.dispatchAllInvocationEvents()
    }
  }
}
