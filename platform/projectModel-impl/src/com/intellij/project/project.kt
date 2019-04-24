// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  return project.isDirectoryBased && filePath.equals(project.stateStore.storageManager.expandMacros(storePath), !SystemInfo.isFileSystemCaseSensitive)
}