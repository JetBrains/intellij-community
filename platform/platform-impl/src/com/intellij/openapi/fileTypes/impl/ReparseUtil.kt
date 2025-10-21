// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.FileContentUtilCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun CoroutineScope.reparseLaterWithCoroutines(changed: List<VirtualFile>) {
  launch(Dispatchers.Default) {
    backgroundWriteAction {
      FileContentUtilCore.reparseFiles(changed)
    }
  }
}