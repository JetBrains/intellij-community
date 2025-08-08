// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileSet2RootDescriptor")

package com.intellij.openapi.roots.impl

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.util.SmartList
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.DummyWorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.impl.LibrariesAndSdkContributors.Companion.getGlobalLibrary
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleSourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.StoredFileSet
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdk

internal fun processFileSet(set: WorkspaceFileSetWithCustomData<*>?, result: SmartList<RootDescriptor?>, snapshot: ImmutableEntityStorage) {
  if (set is StoredFileSet) {
    val data: WorkspaceFileSetData = set.data
    if (data is DummyWorkspaceFileSetData) {
      result.add(DummyRootDescriptor(set.root))
    }
    else {
      val entity = (set as StoredFileSet).entityPointer.resolve(snapshot)
      if (entity is LibraryEntity) {
        val library = entity.findLibraryBridge(snapshot)
        if (library != null) {
          result.add(LibraryRootDescriptor(set.root, library))
        }
      }
      else if (data is ModuleSourceRootData) {
        val module = data.module
        result.add(ModuleRootDescriptor(set.root, module))
      }
      else {
        val sdk: Sdk? = snapshot.findSdk(set)
        if (sdk != null) {
          result.add(SdkRootDescriptor(set.root, sdk))
        }

        val globalLibrary = getGlobalLibrary(set)
        if (globalLibrary != null) {
          result.add(LibraryRootDescriptor(set.root, globalLibrary))
        }

        if (ProjectFileIndexImpl.LOG.isTraceEnabled()) {
          ProjectFileIndexImpl.LOG.trace("Unexpected data: " + data)
        }
      }
    }
  }
  else {
    if (ProjectFileIndexImpl.LOG.isTraceEnabled()) {
      ProjectFileIndexImpl.LOG.trace("Unexpected file set: " + set)
    }
  }
}
