// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity

internal class CustomEntityProjectModelInfoProviderBridge<E : WorkspaceEntity>(private val provider: CustomEntityProjectModelInfoProvider<E>) 
  : WorkspaceFileIndexContributor<E> {
  
  override val entityClass: Class<E>
    get() = provider.entityClass

  override fun registerFileSets(entity: E, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    provider.getContentRoots(sequenceOf(entity), storage).forEach {
      val module = it.parentModule.findModule(storage)
      if (module != null) {
        registrar.registerFileSet(it.root, WorkspaceFileKind.CONTENT, entity, ModuleContentRootData(module, it.root))
      }
    }
    provider.getLibraryRoots(sequenceOf(entity), storage).forEach { libraryRoots ->
      libraryRoots.classes.forEach { 
        registrar.registerFileSet(it, WorkspaceFileKind.EXTERNAL, entity, LibraryRootFileSetData(null, ""))
      }
      libraryRoots.sources.forEach { 
        registrar.registerFileSet(it, WorkspaceFileKind.EXTERNAL_SOURCE, entity, LibrarySourceRootFileSetData(null, ""))
      }
      libraryRoots.excluded.forEach { 
        registrar.registerExcludedRoot(it, WorkspaceFileKind.EXTERNAL, entity)
      }
      libraryRoots.excludeFileCondition?.let { condition ->
        val allRoots = libraryRoots.classes + libraryRoots.sources
        val predicate = condition.transformToCondition(allRoots)
        for (root in allRoots) {
          registrar.registerExclusionCondition(root, { predicate.value(it) }, entity)
        }
      }
    }
    provider.getExcludeSdkRootStrategies(sequenceOf(entity), storage).forEach { excludeStrategy ->
      excludeStrategy.excludeUrls.forEach { 
        registrar.registerExcludedRoot(it, entity)
      }
      //todo excludeStrategy.excludeSdkRootsStrategy? currently it's always null
    }
  }
}