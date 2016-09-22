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
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
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

@JvmOverloads
fun isValidProjectPath(path: String, fastCheckIpr: Boolean = false): Boolean {
  if (fastCheckIpr && path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
    return true
  }

  val file = try {
    Paths.get(path)
  }
  catch (e: InvalidPathException) {
    return false
  }

  val attributes = file.basicAttributesIfExists() ?: return false
  return !attributes.isDirectory /* ipr */ || file.resolve(Project.DIRECTORY_STORE_FOLDER).exists()
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