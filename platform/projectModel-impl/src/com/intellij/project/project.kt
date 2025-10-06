// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project

import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * The project store of this project. Applicable only to projects that have such store; the Default project doesn't have one.
 * @exception IllegalStateException when this project doesn't have a project store.
 */
val Project.stateStore: IProjectStore
  @Throws(IllegalStateException::class)
  get() =
    if (this is ProjectStoreOwner) componentStore
    else throw IllegalStateException("This property is applicable only for projects that are owners of an IProjectStore")

@ApiStatus.Internal
interface ProjectStoreOwner : ComponentStoreOwner {
  override val componentStore: IProjectStore
}

val Project.isDirectoryBased: Boolean
  get() = !isDefault && this is ProjectStoreOwner && componentStore.storeDescriptor.dotIdea != null

fun isEqualToProjectFileStorePath(project: Project, filePath: Path, storePath: String): Boolean =
  project.isDirectoryBased && filePath == project.stateStore.storageManager.expandMacro(storePath)
