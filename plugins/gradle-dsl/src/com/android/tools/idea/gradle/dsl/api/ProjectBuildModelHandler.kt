/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.api


import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Provides an api to use a [ProjectBuildModel] while refreshing the model if the Gradle build files change,
 * this means that both [read] or [modify] could result in reparsing the Gradle build files.
 *
 * Also provides synchronization by using a [ReentrantReadWriteLock] to guard both [read] and [modify].
 * These methods should not be called from the UI thread.
 *
 * This handler shares an instance of the [ProjectBuildModel] and commits all changes on every call to
 * [modify] if you require more fine grained control please use [ProjectBuildModel.get].
 */
interface ProjectBuildModelHandler {

  companion object {
    fun getInstance(project: Project) : ProjectBuildModelHandler = ServiceManager.getService(project, ProjectBuildModelHandler::class.java)
  }


  fun <T> read(block: ProjectBuildModel.() -> T): T

  /**
   * Executes a code [block] which modifies the project model. Obtaining the model can take a long time, so this method must not be called
   * from the EDT. Once the block has been executed, this method will handle applying the model changes on the EDT, blocking the calling
   * thread until the changes have been applied.
   */
  fun <T> modify(block: ProjectBuildModel.() -> T): T
}