// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot.InclusionOptions.*
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.asSafely
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex

class LibraryRootFileIndexContributor : WorkspaceFileIndexContributor<LibraryEntity>, PlatformInternalWorkspaceFileIndexContributor {
  override val entityClass: Class<LibraryEntity> get() = LibraryEntity::class.java

  override fun registerFileSets(entity: LibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val libraryId = entity.symbolicId
    if (libraryId.tableId !is LibraryTableId.ModuleLibraryTableId &&
        storage.referrers(libraryId, ModuleEntity::class.java).none()) {
      return
    }
    val compiledRootsData = LibraryRootFileSetData(libraryId)
    val sourceRootFileSetData = LibrarySourceRootFileSetData(libraryId)
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

  override val dependenciesOnOtherEntities: List<DependencyDescription<LibraryEntity>>
    get() = listOf(
      DependencyDescription.OnReference(ModuleEntity::class.java) { moduleEntity ->
        val result = mutableListOf<LibraryId>()
        for (dep in moduleEntity.dependencies) {
          if (dep !is LibraryDependency) continue
          result.add(dep.library)
        }
        result.asSequence()
      }
    )

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

  object Util {
    internal fun getLibraryId(data: WorkspaceFileSetData): LibraryId? {
      return data.asSafely<LibraryRootFileSetData>()?.libraryId
    }

    internal fun getModuleLibraryId(fileSet: WorkspaceFileSet, storage: EntityStorage): LibraryId? {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer?.resolve(storage).asSafely<LibraryEntity>()?.symbolicId
    }
  }
}

internal class LibrarySourceRootFileSetData(libraryId: LibraryId?)
  : LibraryRootFileSetData(libraryId), ModuleOrLibrarySourceRootData, JvmPackageRootDataInternal

internal open class LibraryRootFileSetData(internal val libraryId: LibraryId?) : UnloadableFileSetData, JvmPackageRootDataInternal {
  override fun isUnloaded(project: Project): Boolean {
    return libraryId != null && !ModuleDependencyIndex.getInstance(project).hasDependencyOn(libraryId)
  }

  override val packagePrefix: String = ""
}

/**
 * Provides a way to exclude [WorkspaceFileSet] with custom data from [WorkspaceFileIndex] based on some condition. This is a temporary
 * solution to exclude project-level libraries which aren't used in modules from the index.
 */
internal interface UnloadableFileSetData : WorkspaceFileSetData {
  fun isUnloaded(project: Project): Boolean
}
