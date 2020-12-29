// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project

interface JpsProjectLoadingManager {
  /**
   * Schedule a task that should be executed after JPS project model will be loaded.
   * Executes the task immediately if JPS is already loaded or puts in to the queue otherwise
   *
   * @see JpsProjectLoadedListener
   */
  fun jpsProjectLoaded(action: Runnable)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JpsProjectLoadingManager {
      return project.getService(JpsProjectLoadingManager::class.java)
    }
  }
}
