// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.jps.*
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.platform.workspace.storage.DummyParentEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LegacyBridgeJpsEntitySourceFactoryImpl(val project: Project) : LegacyBridgeJpsEntitySourceFactoryInternal {
  override fun createEntitySourceForModule(
    baseModuleDir: VirtualFileUrl,
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames?,
    moduleFileName: String?,
  ): EntitySource {
    val internalSource = if (fileInDirectoryNames != null && moduleFileName != null) {
      fileInDirectoryNames.findSource(ModuleEntity::class.java, moduleFileName)
      ?: createInternalEntitySourceForModule(project, baseModuleDir)
    }
    else {
      createInternalEntitySourceForModule(project, baseModuleDir)
    }
    return createImportedEntitySource(project, externalSource, internalSource)
  }

  override fun createEntitySourceForModule(baseModuleDir: VirtualFileUrl, externalSource: ProjectModelExternalSource?): EntitySource {
    return createEntitySourceForModule(
      baseModuleDir = baseModuleDir,
      externalSource = externalSource,
      fileInDirectoryNames = null,
      moduleFileName = null,
    )
  }

  private fun createImportedEntitySource(
    project: Project,
    externalSource: ProjectModelExternalSource?,
    internalSource: JpsFileEntitySource?,
  ): EntitySource {
    val internalFile = internalSource ?: return NonPersistentEntitySource
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  private fun createInternalEntitySourceForModule(
    project: Project,
    baseModuleDir: VirtualFileUrl,
  ): JpsFileEntitySource? {
    val location = getJpsProjectConfigLocation(project) ?: return null
    return JpsProjectFileEntitySource.FileInDirectory(baseModuleDir, location)
  }

  override fun createEntitySourceForProjectLibrary(
    externalSource: ProjectModelExternalSource?,
    fileInDirectoryNames: FileInDirectorySourceNames?,
    fileName: String?,
  ): EntitySource {
    val internalEntitySource: JpsFileEntitySource? = if (fileInDirectoryNames != null && fileName != null) {
      fileInDirectoryNames.findSource(LibraryEntity::class.java, fileName) ?: createInternalEntitySourceForProjectLibrary(project)
    }
    else {
      createInternalEntitySourceForProjectLibrary(project)
    }
    return createImportedEntitySource(project, externalSource, internalEntitySource)
  }

  override fun createEntitySourceForProjectLibrary(externalSource: ProjectModelExternalSource?): EntitySource {
    return createEntitySourceForProjectLibrary(externalSource, null, null)
  }

  override fun createEntitySourceForProjectSettings(): EntitySource? {
    if (project.isDefault) {
      return null
    }

    return when (val configLocation = getJpsProjectConfigLocation(project)) {
      is JpsProjectConfigLocation.FileBased ->
        JpsProjectFileEntitySource.ExactFile(configLocation.iprFile, configLocation)
      is JpsProjectConfigLocation.DirectoryBased -> {
        val miscFile = configLocation.ideaFolder.append("misc.xml")
        JpsProjectFileEntitySource.ExactFile(miscFile, configLocation)
      }
      // This is to support com.intellij.openapi.project.impl.ServerProject which is not-directory-based, but has no project file
      // We store changes in the memory until the project is closed, allowing to adopt settings entity if needed.
      else -> DummyParentEntitySourceForProjectSettings
    }
  }

  private object DummyParentEntitySourceForProjectSettings : DummyParentEntitySource

  private fun createInternalEntitySourceForProjectLibrary(project: Project): JpsFileEntitySource? {
    val location = getJpsProjectConfigLocation(project) ?: return null
    return JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(location)
  }
}