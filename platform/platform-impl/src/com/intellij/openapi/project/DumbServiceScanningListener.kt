// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.Service
import com.intellij.util.indexing.IndexingBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class DumbServiceScanningListener(private val project: Project, private val cs: CoroutineScope) {
  fun subscribe() {
    subscribe(UnindexedFilesScannerExecutor.getInstance(project).isRunning)
  }

  private fun subscribe(scanningState: StateFlow<Boolean>) {
    cs.launch {
      while (true) {
        scanningState.first { it }

        DumbService.getInstance(project).suspendIndexingAndRun(IndexingBundle.message("progress.indexing.scanning")) {
          scanningState.first { !it }
        }
      }
    }
  }

  @TestOnly
  class TestCompanion(private val obj: DumbServiceScanningListener) {
    fun subscribe(scanningState: StateFlow<Boolean>): Unit = obj.subscribe(scanningState)
  }
}
