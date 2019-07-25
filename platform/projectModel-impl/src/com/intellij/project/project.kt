// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.project

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile

val Project.stateStore: IProjectStore
  get() = (this as ComponentManager).stateStore as IProjectStore

interface ProjectStoreOwner {
  fun getComponentStore(): IComponentStore
}

val Project.isDirectoryBased: Boolean
  get() = !isDefault && StorageScheme.DIRECTORY_BASED == (stateStore as IProjectStore).storageScheme

fun getProjectStoreDirectory(file: VirtualFile): VirtualFile? {
  return if (file.isDirectory) file.findChild(Project.DIRECTORY_STORE_FOLDER) else null
}

fun isEqualToProjectFileStorePath(project: Project, filePath: String, storePath: String): Boolean {
  return project.isDirectoryBased && filePath.equals(project.stateStore.storageManager.expandMacros(storePath), !SystemInfo.isFileSystemCaseSensitive)
}