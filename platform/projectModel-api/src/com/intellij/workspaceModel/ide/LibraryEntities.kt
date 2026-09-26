// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryEntities")
package com.intellij.workspaceModel.ide

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.PathUtil

fun getPresentableLibraryName(libraryId: LibraryId?, libraryRootUrl: String?): String {
  return if (libraryId != null) {
    LibraryNameGenerator.getLegacyLibraryName(libraryId) ?: getPresentableNameForLibraryRoot(libraryRootUrl)
  } else {
    getPresentableNameForLibraryRoot(libraryRootUrl)
  }
}

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
  return getPresentableNameForLibraryRoot(url)
}

private fun getPresentableNameForLibraryRoot(libraryRootUrl: String?): String {
  return if (libraryRootUrl != null) PathUtil.toPresentableUrl(libraryRootUrl) else ProjectModelBundle.message("empty.library.title")
}