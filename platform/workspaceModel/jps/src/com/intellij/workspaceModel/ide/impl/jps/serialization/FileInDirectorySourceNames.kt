// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

/**
 * This class is used to reuse [JpsFileEntitySource.FileInDirectory] instances when project is synchronized with JPS files after loading
 * storage from binary cache.
 */
class FileInDirectorySourceNames private constructor(entitiesBySource: Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>) {
  private val mainEntityToSource: Map<Pair<Class<out WorkspaceEntity>, String>, JpsFileEntitySource.FileInDirectory>

  init {
    val sourcesMap = HashMap<Pair<Class<out WorkspaceEntity>, String>, JpsFileEntitySource.FileInDirectory>()
    for ((source, entities) in entitiesBySource) {
      val (type, entityName) = when {
        ModuleEntity::class.java in entities -> ModuleEntity::class.java to (entities.getValue(ModuleEntity::class.java).first() as ModuleEntity).name
        FacetEntity::class.java in entities -> ModuleEntity::class.java to (entities.getValue(FacetEntity::class.java).first() as FacetEntity).module.name
        LibraryEntity::class.java in entities -> LibraryEntity::class.java to (entities.getValue(LibraryEntity::class.java).first() as LibraryEntity).name
        ArtifactEntity::class.java in entities -> ArtifactEntity::class.java to (entities.getValue(ArtifactEntity::class.java).first() as ArtifactEntity).name
        else -> null to null
      }
      if (type != null && entityName != null) {
        val fileName = when {
          // At external storage libs and artifacts store in its own file and at [JpsLibrariesExternalFileSerializer]/[JpsArtifactEntitiesSerializer]
          // we can distinguish them only by directly entity name
          (type == LibraryEntity::class.java || type == ArtifactEntity::class.java) && (source as? JpsImportedEntitySource)?.storedExternally == true -> entityName
          // Module file stored at external and internal modules.xml has .iml file extension
          type == ModuleEntity::class.java -> "$entityName.iml"
          // In internal store (i.e. under `.idea` folder) each library or artifact has its own file, and we can distinguish them only by file name
          else -> FileUtil.sanitizeFileName(entityName) + ".xml"
        }
        sourcesMap[type to fileName] = getInternalFileSource(source) as JpsFileEntitySource.FileInDirectory
      }
    }
    mainEntityToSource = sourcesMap
  }

  fun findSource(entityClass: Class<out WorkspaceEntity>, fileName: String): JpsFileEntitySource.FileInDirectory? =
    mainEntityToSource[entityClass to fileName]

  companion object {
    fun from(storage: WorkspaceEntityStorage) = FileInDirectorySourceNames(
      storage.entitiesBySource { getInternalFileSource(it) is JpsFileEntitySource.FileInDirectory }
    )

    fun empty() = FileInDirectorySourceNames(emptyMap())
  }
}