/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

import java.awt.*

@State(name = "ProjectFrameBounds", storages = Storage(StoragePathMacros.WORKSPACE_FILE))
class ProjectFrameBounds(private val myProject: Project) : PersistentStateComponent<Rectangle> {
  var bounds: Rectangle? = null
    private set

  override fun getState(): Rectangle? {
    return WindowManager.getInstance().getFrame(myProject)!!.bounds
  }

  override fun loadState(state: Rectangle) {
    bounds = state
  }

  companion object {
    fun getInstance(project: Project): ProjectFrameBounds {
      return ServiceManager.getService(project, ProjectFrameBounds::class.java)
    }
  }
}
