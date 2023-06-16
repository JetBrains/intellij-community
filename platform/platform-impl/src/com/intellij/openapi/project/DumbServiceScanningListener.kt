// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.indexing.IndexingBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class DumbServiceScanningListener(private val project: Project, private val cs: CoroutineScope) {
  fun subscribe() {
    subscribe(project.service<UnindexedFilesScannerExecutor>().isRunning)
  }

  private fun subscribe(scanningState: StateFlow<Boolean>) {
    cs.launch {
      while (true) {
        scanningState.first { it }

        project.service<DumbService>().suspendIndexingAndRun(IndexingBundle.message("progress.indexing.scanning")) {
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
