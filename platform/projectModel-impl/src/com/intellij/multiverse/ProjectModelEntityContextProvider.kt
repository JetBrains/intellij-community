// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.multiverse

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextProvider
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetRecognizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdk
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus

internal class ProjectModelEntityContextProvider : CodeInsightContextProvider {

  override fun getContexts(file: VirtualFile, project: Project): List<CodeInsightContext> {
    val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)

    val fileSets = workspaceFileIndex.findFileSets(
      file = file,
      honorExclusion = true,
      includeContentSets = true,
      includeContentNonIndexableSets = true,
      includeExternalSets = true,
      includeExternalSourceSets = true,
      includeCustomKindSets = true
    )
    if (fileSets.isEmpty()) return emptyList()

    val storage = WorkspaceModel.getInstance(project).currentSnapshot

    val contexts = fileSets
      .mapNotNull { fileSet -> extractContext(fileSet, storage, project) }
      .distinct()

    return contexts
  }

  private fun extractContext(
    fileSet: WorkspaceFileSet,
    storage: ImmutableEntityStorage,
    project: Project,
  ): CodeInsightContext? {
    val entityPointer = WorkspaceFileSetRecognizer.getEntityPointer(fileSet)
    if (entityPointer != null) {
      return extractContextFromPointer(entityPointer, storage, project)
    }

    return null
  }

  private fun extractContextFromPointer(
    entityPointer: EntityPointer<*>,
    storage: ImmutableEntityStorage,
    project: Project,
  ): CodeInsightContext? {
    val entity = entityPointer.resolve(storage) ?: return null

    if (entity is SourceRootEntity) {
      val modulePointer = entity.contentRoot.module.createPointer<ModuleEntity>()
      return ModuleContextImpl(modulePointer, project)
    }

    if (entity is ContentRootEntity) {
      val modulePointer = entity.module.createPointer<ModuleEntity>()
      return ModuleContextImpl(modulePointer, project)
    }

    if (entity is LibraryEntity) {
      return LibraryContextImpl(entity.createPointer(), project)
    }

    if (entity is SdkEntity) {
      return SdkContextImpl(entity.createPointer(), project)
    }

    return null
  }

  override fun invalidationRequestFlow(project: Project): Flow<Unit> {
    val eventLog = WorkspaceModel.Companion.getInstance(project).eventLog
    return eventLog.mapNotNull { change ->
      Unit.takeIf { change.getChanges(ModuleEntity::class.java).isNotEmpty() }
    }
  }
}

@ApiStatus.Internal
class ModuleContextImpl(
  private val modulePointer: EntityPointer<ModuleEntity>,
  private val project: Project
) : ModuleContext {
  override fun getModule(): Module? {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val entity = modulePointer.resolve(storage) ?: return null
    return entity.findModule(storage)
  }

  override fun equals(other: Any?): Boolean {
    return modulePointer == (other as? ModuleContextImpl)?.modulePointer
  }

  override fun hashCode(): Int {
    return modulePointer.hashCode()
  }
}

@ApiStatus.Internal
class LibraryContextImpl(
  private val libraryPointer: EntityPointer<LibraryEntity>,
  private val project: Project,
) : LibraryContext {

  override fun getLibrary(): Library? {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val entity = libraryPointer.resolve(storage) ?: return null
    return entity.findLibraryBridge(storage)
  }

  override fun equals(other: Any?): Boolean {
    return libraryPointer == (other as? LibraryContextImpl)?.libraryPointer
  }

  override fun hashCode(): Int {
    return libraryPointer.hashCode()
  }
}

@ApiStatus.Internal
class SdkContextImpl(
  private val sdkPointer: EntityPointer<SdkEntity>,
  private val project: Project,
) : SdkContext {

  override fun getSdk(): Sdk? {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val entity = sdkPointer.resolve(storage) ?: return null
    return storage.findSdk(entity)
  }

  override fun equals(other: Any?): Boolean {
    return sdkPointer == (other as? SdkContextImpl)?.sdkPointer
  }

  override fun hashCode(): Int {
    return sdkPointer.hashCode()
  }
}
