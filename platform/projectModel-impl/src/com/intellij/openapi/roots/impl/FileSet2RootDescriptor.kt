// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FileSet2RootDescriptor")

package com.intellij.openapi.roots.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.impl.DummyWorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleSourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.MultipleWorkspaceFileSets
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdk
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryBridge

/**
 * The descriptors of the module-source and library-classes roots in [info] - the body of the plural
 * [com.intellij.openapi.roots.ProjectFileIndex.getModuleSourceOrLibraryClassesRoots], which the multiverse
 * `RootContainerScope` calls on every `contains` and twice per `compare`.
 *
 * Shaped for the common case of a file with one owner: the info then *is* the file set, and the file set already
 * carries its descriptor ([WorkspaceFileSetImpl.cachedRootDescriptor]), so a hit allocates one singleton list and
 * resolves nothing - no workspace-model snapshot, no entity pointer, no bridge lookup. That is what puts this call
 * on par with the singular [com.intellij.openapi.roots.ProjectFileIndex.getModuleSourceOrLibraryClassesRoot],
 * which returns the bare root. Only the first query of a file set resolves its owner, and only a file with several
 * file sets builds a collection.
 */
internal fun findModuleSourceOrLibraryClassesRootDescriptors(info: WorkspaceFileInternalInfo, project: Project): Collection<RootDescriptor> =
  when (info) {
    is WorkspaceFileInternalInfo.NonWorkspace -> emptyList()
    is WorkspaceFileSetImpl ->
      if (info.isModuleSourceOrLibraryClassesRoot) listOfNotNull(findFileSetDescriptor(info, project))
      else emptyList()
    is MultipleWorkspaceFileSets -> findDescriptors(info, project)
  }

/** The kind check of the singular `getModuleSourceOrLibraryClassesRoot`; the two must stay in sync. */
private val WorkspaceFileSetImpl.isModuleSourceOrLibraryClassesRoot: Boolean
  get() = kind.isContent && data is ModuleOrLibrarySourceRootData || kind == WorkspaceFileKind.EXTERNAL

/** Distinct descriptors in file-set order; the single-descriptor result avoids the set. */
private fun findDescriptors(info: MultipleWorkspaceFileSets, project: Project): Collection<RootDescriptor> {
  var single: RootDescriptor? = null
  var multiple: LinkedHashSet<RootDescriptor>? = null
  for (fileSet in info.fileSets) {
    if (fileSet !is WorkspaceFileSetImpl || !fileSet.isModuleSourceOrLibraryClassesRoot) continue
    val descriptor = findFileSetDescriptor(fileSet, project) ?: continue
    when {
      single == null -> single = descriptor
      multiple != null -> multiple.add(descriptor)
      descriptor != single -> {
        multiple = LinkedHashSet()
        multiple.add(single)
        multiple.add(descriptor)
      }
    }
  }
  return multiple ?: listOfNotNull(single)
}

/**
 * This file set's descriptor, resolved on the first call and served from the file set afterwards. A failed
 * resolution (say, a library whose bridge is not registered yet) is not cached, so it is retried on the next call,
 * which is what the uncached code did too.
 */
internal fun findFileSetDescriptor(set: WorkspaceFileSetImpl, project: Project): RootDescriptor? {
  set.cachedRootDescriptor?.let { return it }
  val descriptor = computeFileSetDescriptor(set, WorkspaceModel.getInstance(project).currentSnapshot) ?: return null
  set.cachedRootDescriptor = descriptor
  return descriptor
}

private fun computeFileSetDescriptor(
  set: WorkspaceFileSetImpl,
  snapshot: ImmutableEntityStorage,
): RootDescriptor? {
  val data = set.data
  if (data is DummyWorkspaceFileSetData) {
    return DummyRootDescriptor(set.root)
  }

  val entity = set.entityPointer.resolve(snapshot)
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

  log.trace { "Unexpected data: $data" }
  return null
}

private val log = fileLogger()
