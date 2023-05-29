// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.asSafely
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId

object WorkspaceFileSetRecognizer {

  fun getModuleForContent(fileSet: WorkspaceFileSet): Module? {
    if (fileSet.kind != WorkspaceFileKind.CONTENT && fileSet.kind != WorkspaceFileKind.TEST_CONTENT) return null
    return fileSet.asSafely<WorkspaceFileSetWithCustomData<*>>()?.data.asSafely<ModuleRelatedRootData>()?.module
  }

  fun getEntityReference(fileSet: WorkspaceFileSet): EntityReference<*>? {
    val entityReference = fileSet.asSafely<WorkspaceFileSetImpl>()?.entityReference
    if (entityReference == null) return null
    if (LibrariesAndSdkContributors.isPlaceholderReference(entityReference)) return null
    if (NonIncrementalContributors.isPlaceholderReference(entityReference)) return null
    return entityReference
  }

  fun getLibraryId(fileSet: WorkspaceFileSet, storage: EntityStorage): LibraryId? {
    val fileSetImpl = fileSet as? WorkspaceFileSetImpl
    if (fileSetImpl == null) return null

    val libraryRootFileSetData = fileSetImpl.data as? LibraryRootFileSetData
    if (libraryRootFileSetData == null) return null

    if (getSdk(fileSet) != null) return null

    val globalLibraryId = LibrariesAndSdkContributors.getGlobalLibraryId(fileSetImpl)
    if (globalLibraryId != null) {
      return globalLibraryId
    }
    val projectLibraryId = LibraryRootFileIndexContributor.getProjectLibraryId(fileSetImpl.data)
    if (projectLibraryId != null) {
      return projectLibraryId
    }

    val moduleLibraryId = LibraryRootFileIndexContributor.getModuleLibraryId(fileSetImpl, storage)
    thisLogger().assertTrue(moduleLibraryId != null) {
      "Failed to find libraryId for $fileSet"
    }
    return moduleLibraryId
  }

  fun getSdk(fileSet: WorkspaceFileSet): Sdk? {
    return LibrariesAndSdkContributors.getSdk(fileSet)
  }

  fun isFromAdditionalLibraryRootsProvider(fileSet: WorkspaceFileSet): Boolean {
    return NonIncrementalContributors.isFromAdditionalLibraryRootsProvider(fileSet)
  }
}