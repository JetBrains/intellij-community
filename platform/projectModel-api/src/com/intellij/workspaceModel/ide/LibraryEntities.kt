// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryEntities")
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.PathUtil

/**
 * Returns the user-visible name of this [LibraryEntity]
 *
 * @return name of this [LibraryEntity] to be shown to user.
 */
val LibraryEntity.presentableName: String
  get() {
    return LibraryNameGenerator.getLegacyLibraryName(symbolicId) ?: getPresentableNameForUnnamedLibrary()
  }

private fun LibraryEntity.getPresentableNameForUnnamedLibrary(): String {
  val url = roots.firstOrNull { it.type == LibraryRootTypeId.COMPILED }?.url?.url
  return if (url != null) PathUtil.toPresentableUrl(url) else ProjectModelBundle.message("empty.library.title")
}