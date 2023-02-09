// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.util.asSafely
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRoot.InclusionOptions.*
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRootTypeId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

class LibraryRootFileIndexContributor : WorkspaceFileIndexContributor<LibraryEntity>, PlatformInternalWorkspaceFileIndexContributor {
  override val entityClass: Class<LibraryEntity> get() = LibraryEntity::class.java

  override fun registerFileSets(entity: LibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (entity.symbolicId.tableId is LibraryTableId.GlobalLibraryTableId) return
    val projectLibraryId = entity.symbolicId.takeIf { it.tableId == LibraryTableId.ProjectLibraryTableId }
    val compiledRootsData = LibraryRootFileSetData(projectLibraryId, "")
    val sourceRootFileSetData = LibrarySourceRootFileSetData(projectLibraryId, "")
    for (root in entity.roots) {
      val data: LibraryRootFileSetData
      val kind: WorkspaceFileKind
      when (root.type) {
        LibraryRootTypeId.COMPILED -> {
          data = compiledRootsData
          kind = WorkspaceFileKind.EXTERNAL
        }
        LibraryRootTypeId.SOURCES -> {
          data = sourceRootFileSetData
          kind = WorkspaceFileKind.EXTERNAL_SOURCE
        } 
        else -> continue
      }
      when (root.inclusionOptions) {
        ROOT_ITSELF -> registrar.registerFileSet(root.url, kind, entity, data)
        ARCHIVES_UNDER_ROOT -> registerArchivesUnderRoot(root.url, registrar, data, kind, entity)
        ARCHIVES_UNDER_ROOT_RECURSIVELY -> registerArchivesUnderRootRecursively(root.url, registrar, data, kind, entity)
      }
    }
  }

  private fun registerArchivesUnderRoot(root: VirtualFileUrl,
                                        registrar: WorkspaceFileSetRegistrar,
                                        data: LibraryRootFileSetData,
                                        kind: WorkspaceFileKind,
                                        entity: LibraryEntity
  ) {
    root.virtualFile?.children?.forEach { file ->
      if (!file.isDirectory && FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence) === ArchiveFileType.INSTANCE) {
        val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR)
        if (jarRoot != null) {
          registrar.registerFileSet(jarRoot, kind, entity, data)
        }
      }
    }
  }

  private fun registerArchivesUnderRootRecursively(root: VirtualFileUrl,
                                                   registrar: WorkspaceFileSetRegistrar,
                                                   data: LibraryRootFileSetData,
                                                   kind: WorkspaceFileKind,
                                                   entity: LibraryEntity) {
    val virtualFile = root.virtualFile ?: return
    VfsUtilCore.visitChildrenRecursively(virtualFile, object : VirtualFileVisitor<Void?>() {
      override fun visitFile(file: VirtualFile ): Boolean {
        if (!file.isDirectory && FileTypeRegistry.getInstance().getFileTypeByFileName(file.nameSequence) === ArchiveFileType.INSTANCE) {
          val jarRoot = StandardFileSystems.jar().findFileByPath(file.path + URLUtil.JAR_SEPARATOR)
          if (jarRoot != null) {
            registrar.registerFileSet(jarRoot, kind, entity, data)
            return false
          }
        }
        return true
      }
    })
  }

  companion object {
    internal fun getProjectLibraryId(data: WorkspaceFileSetData): LibraryId? {
      return data.asSafely<LibraryRootFileSetData>()?.projectLibraryId
    }

    fun getModuleLibraryId(fileSet: WorkspaceFileSet, storage: EntityStorage): LibraryId? {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityReference?.resolve(storage).asSafely<LibraryEntity>()?.symbolicId
    }
  }
}

internal class LibrarySourceRootFileSetData(projectLibraryId: LibraryId?, packagePrefix: String) 
  : LibraryRootFileSetData(projectLibraryId, packagePrefix), ModuleOrLibrarySourceRootData, JvmPackageRootDataInternal

internal open class LibraryRootFileSetData(internal val projectLibraryId: LibraryId?,
                                           override val packagePrefix: String) : UnloadableFileSetData, JvmPackageRootDataInternal {
  override fun isUnloaded(project: Project): Boolean {
    return projectLibraryId != null && !ModuleDependencyIndex.getInstance(project).hasDependencyOn(projectLibraryId)
  }
}

/**
 * Provides a way to exclude [WorkspaceFileSet] with custom data from [WorkspaceFileIndex] based on some condition. This is a temporary
 * solution to exclude project-level libraries which aren't used in modules from the index.
 */
internal interface UnloadableFileSetData : WorkspaceFileSetData {
  fun isUnloaded(project: Project): Boolean
}
