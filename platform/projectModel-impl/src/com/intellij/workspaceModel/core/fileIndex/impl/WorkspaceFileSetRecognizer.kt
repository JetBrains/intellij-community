// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.asSafely
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.SdkEntityFileIndexContributor.SdkRootFileSetData
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryId

object WorkspaceFileSetRecognizer {

  fun getModuleForContent(fileSet: WorkspaceFileSet): Module? {
    if (fileSet.kind != WorkspaceFileKind.CONTENT && fileSet.kind != WorkspaceFileKind.TEST_CONTENT) return null
    return fileSet.asSafely<WorkspaceFileSetWithCustomData<*>>()?.data.asSafely<ModuleRelatedRootData>()?.module
  }

  fun getEntityPointer(fileSet: WorkspaceFileSet): EntityPointer<*>? {
    val entityReference = fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer
    if (entityReference == null) return null
    if (!Registry.`is`("ide.workspace.model.sdk.remove.custom.processing") && LibrariesAndSdkContributors.isPlaceholderReference(entityReference)) return null
    if (NonIncrementalContributors.isPlaceholderReference(entityReference)) return null
    return entityReference
  }

  fun getLibraryId(fileSet: WorkspaceFileSet, storage: EntityStorage): LibraryId? {
    val fileSetImpl = fileSet as? WorkspaceFileSetImpl
    if (fileSetImpl == null) return null

    val libraryRootFileSetData = fileSetImpl.data as? LibraryRootFileSetData
    if (libraryRootFileSetData == null) return null

    if (!Registry.`is`("ide.workspace.model.sdk.remove.custom.processing")) {
      if (getSdk(fileSet) != null) return null

      val globalLibraryId = LibrariesAndSdkContributors.getGlobalLibrary(fileSetImpl)?.let {
        findLibraryId(library = it)
      }
      if (globalLibraryId != null) {
        return globalLibraryId
      }
    }

    val libraryId = LibraryRootFileIndexContributor.Util.getLibraryId(fileSetImpl.data)
    if (libraryId != null) {
      return libraryId
    }

    val moduleLibraryId = LibraryRootFileIndexContributor.Util.getModuleLibraryId(fileSetImpl, storage)
    thisLogger().assertTrue(moduleLibraryId != null) {
      "Failed to find libraryId for $fileSet"
    }
    return moduleLibraryId
  }

  fun getSdkId(fileSet: WorkspaceFileSet): SdkId? {
    return if (Registry.`is`("ide.workspace.model.sdk.remove.custom.processing")) {
      fileSet.asSafely<WorkspaceFileSetImpl>()?.data?.asSafely<SdkRootFileSetData>()?.sdkId
    }
    else {
      LibrariesAndSdkContributors.getSdk(fileSet)?.let { SdkId(it.name, it.sdkType.name) }
    }
  }

  private fun getSdk(fileSet: WorkspaceFileSet): Sdk? {
    return LibrariesAndSdkContributors.getSdk(fileSet)
  }

  fun isFromAdditionalLibraryRootsProvider(fileSet: WorkspaceFileSet): Boolean {
    return NonIncrementalContributors.isFromAdditionalLibraryRootsProvider(fileSet)
  }

  fun isSourceRoot(fileSet: WorkspaceFileSet): Boolean {
    return (fileSet as? WorkspaceFileSetImpl)?.data is ModuleSourceRootData
  }
}