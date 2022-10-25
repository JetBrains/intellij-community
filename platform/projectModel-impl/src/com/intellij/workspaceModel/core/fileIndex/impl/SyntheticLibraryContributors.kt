// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity

internal class SyntheticLibraryContributors(private val project: Project, private val rootFileSupplier: RootFileSupplier) {
  private val allRoots = HashSet<VirtualFile>()
  
  internal fun registerFileSets(fileSets: MultiMap<VirtualFile, WorkspaceFileSetImpl>,
                                excludedFileSets: MultiMap<VirtualFile, ExcludedFileSet>) {
    AdditionalLibraryRootsProvider.EP_NAME.extensionList.forEach { provider ->
      provider.getAdditionalProjectLibraries(project).forEach { library ->
        fun registerRoots(files: MutableCollection<VirtualFile>, kind: WorkspaceFileKind) {
          files.forEach { root ->
            rootFileSupplier.correctRoot(root, library, provider)?.let {
              fileSets.putValue(it, WorkspaceFileSetImpl(it, kind, SyntheticLibraryReference, DummyWorkspaceFileSetData))
              allRoots.add(it)
            }
          }
        }
        //todo use comparisonId for incremental updates?
        registerRoots(library.sourceRoots, WorkspaceFileKind.EXTERNAL_SOURCE)
        registerRoots(library.binaryRoots, WorkspaceFileKind.EXTERNAL)
        library.excludedRoots.forEach { 
          excludedFileSets.putValue(it, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.EXTERNAL, SyntheticLibraryReference))
          allRoots.add(it)
        }
        library.unitedExcludeCondition?.let { condition ->
          val predicate = { file: VirtualFile -> condition.value(file) }
          (library.sourceRoots + library.binaryRoots).forEach { root ->
            excludedFileSets.putValue(root, ExcludedFileSet.ByCondition(root, predicate, SyntheticLibraryReference))
          }
        }
      }
    }
  }
  
  internal fun unregisterFileSets(fileSets: MultiMap<VirtualFile, WorkspaceFileSetImpl>,
                                  excludedRoots: MultiMap<VirtualFile, ExcludedFileSet>) {
    for (root in allRoots) {
      fileSets.removeValueIf(root) { it.entityReference is SyntheticLibraryReference }
      excludedRoots.removeValueIf(root) { it.entityReference is SyntheticLibraryReference }
    }
    allRoots.clear()
  }
}

private object SyntheticLibraryReference : EntityReference<WorkspaceEntity>() {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
}

