// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project

import com.intellij.openapi.components.ComponentStoreOwner
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

val Project.stateStore: IProjectStore
  get() = (this as ProjectStoreOwner).componentStore

@ApiStatus.Internal
interface ProjectStoreOwner : ComponentStoreOwner {
  override val componentStore: IProjectStore
}

val Project.isDirectoryBased: Boolean
  get() = !isDefault && StorageScheme.DIRECTORY_BASED == (stateStore as? IProjectStore)?.storageScheme

fun getProjectStoreDirectory(file: VirtualFile): VirtualFile? {
  return if (file.isDirectory) file.findChild(Project.DIRECTORY_STORE_FOLDER) else null
}

fun isEqualToProjectFileStorePath(project: Project, filePath: Path, storePath: String): Boolean {
  return project.isDirectoryBased && filePath == project.stateStore.storageManager.expandMacro(storePath)
}