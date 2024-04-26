// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.asSafely
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryId
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener
import java.util.*

internal class LibrariesAndSdkContributors(private val project: Project,
                                           private val rootFileSupplier: RootFileSupplier,
                                           private val fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
                                           private val fileSetsByPackagePrefix: PackagePrefixStorage
) : ModuleDependencyListener, ProjectRootManagerEx.ProjectJdkListener {
  private val sdkRoots = MultiMap.create<Sdk, VirtualFile>()
  private val libraryRoots = MultiMap<Library, VirtualFile>(IdentityHashMap())
  private val moduleDependencyIndex = ModuleDependencyIndex.getInstance(project)
  private var registeredProjectSdk: Sdk? = null
  private var includeProjectSdk = false
  
  init {
    if (rootFileSupplier == RootFileSupplier.INSTANCE) {
      moduleDependencyIndex.addListener(this)
      ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener(this)
    }
  }
  
  fun registerFileSets() {
    var noSdkIsUsed = true
    ProjectJdkTable.getInstance().allJdks.forEach { sdk ->
      if (moduleDependencyIndex.hasDependencyOn(sdk)) {
        registerSdkRoots(sdk)
        noSdkIsUsed = false
      }
    }
    if (noSdkIsUsed) {
      registerProjectSdkRoots()
    }
    (LibraryTablesRegistrar.getInstance().customLibraryTables.asSequence() + LibraryTablesRegistrar.getInstance().libraryTable).forEach { 
      it.libraries.forEach { library ->
        if (moduleDependencyIndex.hasDependencyOn(library)) {
          registerLibraryRoots(library)
        }  
      }
    }
  }

  private fun registerProjectSdkRoots() {
    unregisterProjectSdkRoots()
    includeProjectSdk = true
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    if (projectSdk != null) {
      registeredProjectSdk = projectSdk
      registerSdkRoots(projectSdk)
    }
  }

  private fun registerLibraryRoots(library: Library) {
    fun registerLibraryRoots(rootType: OrderRootType,
                             kind: WorkspaceFileKind,
                             pointer: GlobalLibraryPointer,
                             data: WorkspaceFileSetData) {
      rootFileSupplier.getLibraryRoots(library, rootType).forEach { root ->
        if (RootFileSupplier.ensureValid(root, library, null)) {
          val fileSet = WorkspaceFileSetImpl(root, kind, pointer, EntityStorageKind.MAIN, data)
          fileSets.putValue(root, fileSet)
          fileSetsByPackagePrefix.addFileSet("", fileSet)
          libraryRoots.putValue(library, root)
        }
      }
    }

    val pointer = GlobalLibraryPointer(library)
    registerLibraryRoots(OrderRootType.CLASSES, WorkspaceFileKind.EXTERNAL, pointer, LibraryRootFileSetData(null, ""))
    registerLibraryRoots(OrderRootType.SOURCES, WorkspaceFileKind.EXTERNAL_SOURCE, pointer, LibrarySourceRootFileSetData(null, ""))
    (library as? LibraryEx)?.let { rootFileSupplier.getExcludedRoots(it) }?.forEach {
      if (RootFileSupplier.ensureValid(it, library, null)) {
        fileSets.putValue(it, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.EXTERNAL, pointer))
        libraryRoots.putValue(library, it)
      }
    }
  }

  private fun registerSdkRoots(sdk: Sdk) {
    fun registerSdkRoots(rootType: OrderRootType, kind: WorkspaceFileKind, pointer: SdkPointer, data: WorkspaceFileSetData) {
      sdk.rootProvider.getUrls(rootType).forEach { url ->
        val root = rootFileSupplier.findFileByUrl(url)
        if (root != null && RootFileSupplier.ensureValid(root, sdk, null)) {
          val fileSet = WorkspaceFileSetImpl(root, kind, pointer, EntityStorageKind.MAIN, data)
          fileSets.putValue(root, fileSet)
          fileSetsByPackagePrefix.addFileSet("", fileSet)
          sdkRoots.putValue(sdk, root)
        }
      }
    }

    val pointer = SdkPointer(sdk)
    registerSdkRoots(OrderRootType.CLASSES, WorkspaceFileKind.EXTERNAL, pointer, LibraryRootFileSetData(null, ""))
    registerSdkRoots(OrderRootType.SOURCES, WorkspaceFileKind.EXTERNAL_SOURCE, pointer, LibrarySourceRootFileSetData(null, ""))
  }

  private fun unregisterSdkRoots(sdk: Sdk) {
    val roots = sdkRoots.remove(sdk)
    roots?.forEach { root ->
      fileSets.removeValueIf(root) { fileSet: StoredFileSet -> (fileSet.entityPointer as? SdkPointer)?.sdk == sdk }
      fileSetsByPackagePrefix.removeByPrefixAndPointer("", SdkPointer(sdk))
    }
  }

  private fun unregisterLibraryRoots(library: Library) {
    val roots = libraryRoots.remove(library)
    roots?.forEach { root ->
      fileSets.removeValueIf(root) { fileSet: StoredFileSet -> (fileSet.entityPointer as? GlobalLibraryPointer)?.library === library }
      fileSetsByPackagePrefix.removeByPrefixAndPointer("", GlobalLibraryPointer(library))
    }
  }

  override fun firstDependencyOnSdkAdded() {
    unregisterProjectSdkRoots()
  }

  private fun unregisterProjectSdkRoots() {
    registeredProjectSdk?.let { unregisterSdkRoots(it) }
    registeredProjectSdk = null
    includeProjectSdk = false
  }

  override fun lastDependencyOnSdkRemoved() {
    registerProjectSdkRoots()
  }

  override fun addedDependencyOn(library: Library) {
    if (shouldListen(library)) {
      registerLibraryRoots(library)
    }
  }

  private fun shouldListen(library: Library) = library.table?.tableLevel != LibraryTablesRegistrar.PROJECT_LEVEL

  override fun referencedLibraryChanged(library: Library) {
    if (shouldListen(library)) {
      unregisterLibraryRoots(library)
      registerLibraryRoots(library)
    }
  }

  override fun removedDependencyOn(library: Library) {
    if (shouldListen(library)) {
      unregisterLibraryRoots(library)
    }
  }

  override fun addedDependencyOn(sdk: Sdk) {
    registerSdkRoots(sdk)
  }

  override fun referencedSdkChanged(sdk: Sdk) {
    unregisterSdkRoots(sdk)
    registerSdkRoots(sdk)
  }

  override fun removedDependencyOn(sdk: Sdk) {
    unregisterSdkRoots(sdk)
  }

  override fun projectJdkChanged() {
    if (includeProjectSdk) {
      registerProjectSdkRoots()
    }
  }

  companion object {
    internal fun getGlobalLibraryId(fileSet: WorkspaceFileSet): LibraryId? {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer.asSafely<GlobalLibraryPointer>()?.library?.let {
        findLibraryId(library = it)
      }
    }

    internal fun getSdk(fileSet: WorkspaceFileSet): Sdk? {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer.asSafely<SdkPointer>()?.sdk
    }

    fun isPlaceholderReference(entityPointer: EntityPointer<WorkspaceEntity>): Boolean {
      return entityPointer is GlobalLibraryPointer || entityPointer is SdkPointer
    }
  }
}

private class GlobalLibraryPointer(val library: Library) : EntityPointer<WorkspaceEntity> {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
  override fun isPointerTo(entity: WorkspaceEntity): Boolean = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GlobalLibraryPointer
    return library === other.library
  }

  override fun hashCode(): Int {
    return System.identityHashCode(library)
  }
}

private class SdkPointer(val sdk: Sdk) : EntityPointer<WorkspaceEntity> {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
  override fun isPointerTo(entity: WorkspaceEntity): Boolean = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SdkPointer
    return sdk === other.sdk
  }

  override fun hashCode(): Int {
    return sdk.hashCode()
  }
}