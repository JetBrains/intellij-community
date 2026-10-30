// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.util

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun VirtualFile.refreshAndAwait() {
  edtWriteAction {
    refresh(false, true)
  }
  withContext(Dispatchers.EDT) {
    // executing means that all invocation events are pumped
  }
}
