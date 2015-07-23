/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.reactiveidea.history.host.HistoryHost
import com.jetbrains.reactiveidea.history.host.historyPath
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.util.Lifetime

class ProjectHost(path: Path, lifetime: Lifetime, initializer: Initializer, val project: Project, reactiveModel: ReactiveModel): Host, DataProvider {
  val startupManager = StartupManager.getInstance(project)

  init {
    startupManager.runWhenProjectIsInitialized {
      reactiveModel.host(path / "project-view") { path, lifetime, initializer ->
        ProjectViewHost(project, reactiveModel, path, lifetime, initializer)
      }
    }
    reactiveModel.host(path / "tab-view") { path, lifetime, initializer ->
      TabViewHost(project, reactiveModel, path)
    }

    reactiveModel.host(historyPath) { path, lifetime, initializer ->
      HistoryHost(reactiveModel, path, lifetime, initializer)
    }
  }

  override fun getData(dataId: String?): Any? {
    if (CommonDataKeys.PROJECT.`is`(dataId)) {
      return project
    }
    return null
  }
}