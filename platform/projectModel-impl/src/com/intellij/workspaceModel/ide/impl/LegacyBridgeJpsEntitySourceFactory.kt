// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspaceModel.jps.*
import com.intellij.platform.workspaceModel.jps.serialization.impl.FileInDirectorySourceNames
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalEntitiesSerializers
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

object LegacyBridgeJpsEntitySourceFactory {
  fun createEntitySourceForModule(project: Project,
                                  baseModuleDir: VirtualFileUrl,
                                  externalSource: ProjectModelExternalSource?,
                                  fileInDirectoryNames: FileInDirectorySourceNames? = null,
                                  moduleFileName: String? = null): EntitySource {
    val internalSource = if (fileInDirectoryNames != null && moduleFileName != null) {
      fileInDirectoryNames.findSource(ModuleEntity::class.java, moduleFileName) ?: createInternalEntitySourceForModule(project, baseModuleDir)
    } else {
      createInternalEntitySourceForModule(project, baseModuleDir)
    }
    return createImportedEntitySource(project, externalSource, internalSource)
  }

  private fun createImportedEntitySource(project: Project,
                                         externalSource: ProjectModelExternalSource?,
                                         internalSource: JpsFileEntitySource?): EntitySource {
    val internalFile = internalSource ?: return NonPersistentEntitySource
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

  private fun createInternalEntitySourceForModule(project: Project,
                                                  baseModuleDir: VirtualFileUrl): JpsFileEntitySource? {
    val location = getJpsProjectConfigLocation(project) ?: return null
    return JpsProjectFileEntitySource.FileInDirectory(baseModuleDir, location)
  }

  fun createEntitySourceForProjectLibrary(project: Project,
                                          externalSource: ProjectModelExternalSource?,
                                          fileInDirectoryNames: FileInDirectorySourceNames? = null,
                                          fileName: String? = null): EntitySource {
    val internalEntitySource: JpsFileEntitySource? = if (fileInDirectoryNames != null && fileName != null) {
      fileInDirectoryNames.findSource(LibraryEntity::class.java, fileName) ?: createInternalEntitySourceForProjectLibrary(project)
    }
    else {
      createInternalEntitySourceForProjectLibrary(project)
    }
    return createImportedEntitySource(project, externalSource, internalEntitySource)
  }

  fun createEntitySourceForGlobalLibrary(): EntitySource {
    val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
    val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(JpsGlobalEntitiesSerializers.GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
    return JpsGlobalFileEntitySource(globalLibrariesFile)
  }

  private fun createInternalEntitySourceForProjectLibrary(project: Project): JpsFileEntitySource? {
    val location = getJpsProjectConfigLocation(project) ?: return null
    return JpsEntitySourceFactory.createJpsEntitySourceForProjectLibrary(location)
  }

  fun createEntitySourceForArtifact(project: Project, externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = JpsEntitySourceFactory.createJpsEntitySourceForArtifact(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }

}