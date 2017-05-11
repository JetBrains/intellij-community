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
package com.intellij.project

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.exists
import java.nio.file.InvalidPathException
import java.nio.file.Paths

val Project.stateStore: IProjectStore
  get() = picoContainer.getComponentInstance(IComponentStore::class.java) as IProjectStore

val Project.isDirectoryBased: Boolean
  get() = !isDefault && StorageScheme.DIRECTORY_BASED == stateStore.storageScheme

fun getProjectStoreDirectory(file: VirtualFile): VirtualFile? {
  return if (file.isDirectory) file.findChild(Project.DIRECTORY_STORE_FOLDER) else null
}

@JvmOverloads
fun isValidProjectPath(path: String, anyRegularFileIsValid: Boolean = false): Boolean {
  val file = try {
    Paths.get(path)
  }
  catch (e: InvalidPathException) {
    return false
  }

  val attributes = file.basicAttributesIfExists() ?: return false
  return if (attributes.isDirectory) {
    file.resolve(Project.DIRECTORY_STORE_FOLDER).exists()
  }
  else {
    anyRegularFileIsValid || path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)
  }
}

fun isEqualToProjectFileStorePath(project: Project, filePath: String, storePath: String): Boolean {
  return project.isDirectoryBased && filePath.equals(project.stateStore.stateStorageManager.expandMacros(storePath), !SystemInfo.isFileSystemCaseSensitive)
}