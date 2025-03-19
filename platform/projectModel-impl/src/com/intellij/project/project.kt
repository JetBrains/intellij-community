// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project

import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The project store of this project. Applicable only to projects that have such store; the Default project doesn't have one.
 * @exception IllegalStateException when this project doesn't have a project store.
 */
val Project.stateStore: IProjectStore
  @Throws(IllegalStateException::class)
  get() = if (this is ProjectStoreOwner) this.componentStore
          else throw IllegalStateException("This property is applicable only for projects that are owners of an IProjectStore")

@ApiStatus.Internal
interface ProjectStoreOwner : ComponentStoreOwner {
  override val componentStore: IProjectStore
}

val Project.isDirectoryBased: Boolean
  get() {
    if (isDefault) {
      return false
    }

    val stateStore = if (this is ComponentStoreOwner) this.componentStore else service()
    return (stateStore as? IProjectStore)?.storageScheme == StorageScheme.DIRECTORY_BASED
  }

fun isEqualToProjectFileStorePath(project: Project, filePath: Path, storePath: String): Boolean {
  return project.isDirectoryBased && filePath == project.stateStore.storageManager.expandMacro(storePath)
}