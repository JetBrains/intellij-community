// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.SharedMetrics
import java.util.concurrent.Semaphore

val workspaceMetrics: WorkspaceMetrics by lazy { WorkspaceMetrics.instance }

class WorkspaceMetrics : SharedMetrics(WorkspaceModel) {
  companion object {
    private val lock = Semaphore(1)
    private var _instance: WorkspaceMetrics? = null

    val instance: WorkspaceMetrics
      get() {
        try {
          if (_instance != null) return _instance!!
          lock.acquire()
          if (_instance == null) _instance = WorkspaceMetrics()
        }
        catch (e: InterruptedException) {
          lock.release()
        }
        finally {
          lock.release()
        }
        return _instance!!
      }

    const val workspaceSyncSpanName = "workspace.sync"
  }
}
