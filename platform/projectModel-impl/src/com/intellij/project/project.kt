/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.project

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import java.nio.file.InvalidPathException
import java.nio.file.Paths

val Project.isDirectoryBased: Boolean
  get() {
    val store = stateStore
    return store is IProjectStore && StorageScheme.DIRECTORY_BASED == store.storageScheme
  }

val Project.stateStore: IProjectStore
  get() {
    return picoContainer.getComponentInstance(IComponentStore::class.java) as IProjectStore
  }

fun getProjectStoreDirectory(file: VirtualFile): VirtualFile? {
  return if (file.isDirectory) file.findChild(Project.DIRECTORY_STORE_FOLDER) else null
}

fun isValidProjectPath(path: String): Boolean {
  val file = try {
    Paths.get(path)
  }
  catch (e: InvalidPathException) {
    return false
  }

  val attributes = file.basicAttributesIfExists() ?: return false
  return if (attributes.isDirectory) file.resolve(Project.DIRECTORY_STORE_FOLDER).exists() else path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)
}

fun isProjectDirectoryExistsUsingIo(parent: VirtualFile): Boolean {
  try {
    return Paths.get(FileUtil.toSystemDependentName(parent.path), Project.DIRECTORY_STORE_FOLDER).exists()
  }
  catch (e: InvalidPathException) {
    return false
  }
}

fun isEqualToProjectFileStorePath(project: Project, filePath: String, storePath: String): Boolean {
  if (!project.isDirectoryBased) {
    return false
  }

  val store = project.stateStore as IProjectStore
  return filePath.equals(store.stateStorageManager.expandMacros(storePath), !SystemInfo.isFileSystemCaseSensitive)
}

inline fun <T> Project.modifyModules(crossinline task: ModifiableModuleModel.() -> T): T {
  val model = ModuleManager.getInstance(this).modifiableModel
  val result = model.task()
  runWriteAction {
    model.commit()
  }
  return result
}

val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

/**
 *  Tries to guess the "main project directory" of the project.
 *
 *  There is no strict definition of what is a project directory, since a project can contain multiple modules located in different places,
 *  and the `.idea` directory can be located elsewhere (making the popular [Project.getBaseDir] method not applicable to get the "project
 *  directory"). This method should be preferred, although it can't provide perfect accuracy either.
 *
 *  @throws IllegalStateException if called on the default project, since there is no sense in "project dir" in that case.
 */
fun Project.guessProjectDir() : VirtualFile {
  if (isDefault) {
    throw IllegalStateException("Not applicable for default project")
  }

  val modules = ModuleManager.getInstance(this).modules
  val module = if (modules.size == 1) modules.first() else modules.find { it.name == this.name }
  if (module != null) {
    val roots = ModuleRootManager.getInstance(module).contentRoots
    roots.firstOrNull()?.let {
      return it
    }
  }
  return this.baseDir!!
}