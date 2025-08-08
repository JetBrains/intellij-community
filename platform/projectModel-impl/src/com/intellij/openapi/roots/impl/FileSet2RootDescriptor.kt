// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileSet2RootDescriptor")

package com.intellij.openapi.roots.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.DummyWorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.impl.LibrariesAndSdkContributors.Companion.getGlobalLibrary
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleSourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.StoredFileSet
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdk

internal fun findFileSetDescriptor(
  set: WorkspaceFileSetWithCustomData<*>,
  snapshot: ImmutableEntityStorage,
): RootDescriptor? {
  if (set !is StoredFileSet) {
    log.trace { "Unexpected file set: $set" }
    return null
  }

  val data = set.data
  if (data is DummyWorkspaceFileSetData) {
    return DummyRootDescriptor(set.root)
  }

  val entity = (set as StoredFileSet).entityPointer.resolve(snapshot)
  if (entity is LibraryEntity) {
    val library = entity.findLibraryBridge(snapshot) ?: run {
      log.trace { "Library is not found for $entity in $snapshot" }
      return null
    }

    return LibraryRootDescriptor(set.root, library)
  }

  if (data is ModuleSourceRootData) {
    val module = data.module
    return ModuleRootDescriptor(set.root, module)
  }

  val sdk = snapshot.findSdk(set)
  if (sdk != null) {
    return SdkRootDescriptor(set.root, sdk)
  }

  val globalLibrary = getGlobalLibrary(set)
  if (globalLibrary != null) {
    return LibraryRootDescriptor(set.root, globalLibrary)
  }

  log.trace { "Unexpected data: $data" }
  return null
}

private val log = fileLogger()