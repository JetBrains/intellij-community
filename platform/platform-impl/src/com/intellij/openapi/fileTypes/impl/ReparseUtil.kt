// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.FileContentUtilCore
import com.intellij.util.ForcefulReparseModificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun CoroutineScope.reparseLaterWithCoroutines(changed: List<VirtualFile>) {
  launch(Dispatchers.Default) {
    if (!Registry.`is`("filetype.reparse.prepare.async.listeners.in.read.action")) {
      backgroundWriteAction {
        FileContentUtilCore.reparseFiles(changed)
      }
      return@launch
    }
    val events = readAction { FileContentUtilCore.createReparseEvents(changed) }
    RefreshQueue.getInstance().processEvents(events)
    ForcefulReparseModificationTracker.increment()
  }
}