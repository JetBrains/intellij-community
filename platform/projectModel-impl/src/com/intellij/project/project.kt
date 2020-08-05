// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.project

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

val Project.stateStore: IProjectStore
  get() = (this as ComponentManager).stateStore as IProjectStore

@ApiStatus.Internal
interface ProjectStoreOwner {
  /**
   * Setter is temporary allowed to allow overriding project store class for a specific project. Overriding ProjectStoreFactory
   * service won't work because a service may be overridden in a single plugin only.
   */
  var componentStore: IProjectStore
}

val Project.isDirectoryBased: Boolean
  get() = !isDefault && StorageScheme.DIRECTORY_BASED == (stateStore as IProjectStore).storageScheme

fun getProjectStoreDirectory(file: VirtualFile): VirtualFile? {
  return if (file.isDirectory) file.findChild(Project.DIRECTORY_STORE_FOLDER) else null
}

fun isEqualToProjectFileStorePath(project: Project, filePath: String, storePath: String): Boolean {
  return project.isDirectoryBased && filePath.equals(project.stateStore.storageManager.expandMacros(storePath), !SystemInfo.isFileSystemCaseSensitive)
}