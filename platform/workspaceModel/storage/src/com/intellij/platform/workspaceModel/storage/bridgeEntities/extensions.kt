// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspaceModel.storage.EntitySource
import com.intellij.platform.workspaceModel.storage.EntityStorage
import com.intellij.platform.workspaceModel.storage.MutableEntityStorage
import com.intellij.platform.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import java.util.*

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addModuleEntity(name: @NlsSafe String,
                                         dependencies: List<ModuleDependencyItem>,
                                         source: EntitySource,
                                         type: @NonNls String? = null): ModuleEntity {
  return this addEntity ModuleEntity(name, dependencies, source) {
    this.type = type
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
                                                     excludeOutput: Boolean,
                                                     compilerOutput: VirtualFileUrl?,
                                                     compilerOutputForTests: VirtualFileUrl?,
                                                     languageLevelId: @NonNls String?,
                                                     module: ModuleEntity,
                                                     source: EntitySource): JavaModuleSettingsEntity {
  return this addEntity JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, source) {
    this.compilerOutput = compilerOutput
    this.compilerOutputForTests = compilerOutputForTests
    this.languageLevelId = languageLevelId
    this.module = module
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addModuleCustomImlDataEntity(rootManagerTagCustomData: @NonNls String?,
                                                      customModuleOptions: Map<String, String>,
                                                      module: ModuleEntity,
                                                      source: EntitySource): ModuleCustomImlDataEntity {
  return this addEntity ModuleCustomImlDataEntity(HashMap(customModuleOptions), source) {
    this.rootManagerTagCustomData = rootManagerTagCustomData
    this.module = module
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addModuleGroupPathEntity(path: List<@NlsSafe String>,
                                                  module: ModuleEntity,
                                                  source: EntitySource): ModuleGroupPathEntity {
  return this addEntity ModuleGroupPathEntity(path, source) {
    this.module = module
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addSourceRootEntity(contentRoot: ContentRootEntity,
                                             url: VirtualFileUrl,
                                             rootType: @NonNls String,
                                             source: EntitySource): SourceRootEntity {
  return this addEntity SourceRootEntity(url, rootType, source) {
    this.contentRoot = contentRoot
  }
}

/**
 * [JavaSourceRootPropertiesEntity] has the same entity source as [SourceRootEntity].
 *
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                 generated: Boolean,
                                                 packagePrefix: @NlsSafe String): JavaSourceRootPropertiesEntity {
  return this addEntity JavaSourceRootPropertiesEntity(generated, packagePrefix, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                   generated: Boolean,
                                                   relativeOutputPath: @NlsSafe String): JavaResourceRootPropertiesEntity {
  return this addEntity JavaResourceRootPropertiesEntity(generated, relativeOutputPath, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                             propertiesXmlTag: @NonNls String): CustomSourceRootPropertiesEntity {
  return this addEntity CustomSourceRootPropertiesEntity(propertiesXmlTag, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addContentRootEntity(url: VirtualFileUrl,
                                              excludedUrls: List<VirtualFileUrl>,
                                              excludedPatterns: List<@NlsSafe String>,
                                              module: ModuleEntity,
                                              source: EntitySource = module.entitySource): ContentRootEntity {
  val excludes = excludedUrls.map { this addEntity ExcludeUrlEntity(it, source) }
  return this addEntity ContentRootEntity(url, excludedPatterns, source) {
    this.excludedUrls = excludes
    this.module = module
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addLibraryEntity(name: @NlsSafe String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                          excludedRoots: List<VirtualFileUrl>, source: EntitySource): LibraryEntity {
  val excludes = excludedRoots.map { this addEntity ExcludeUrlEntity(it, source) }
  return addLibraryEntityWithExcludes(name, tableId, roots, excludes, source)
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addLibraryEntityWithExcludes(name: @NlsSafe String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                      excludedRoots: List<ExcludeUrlEntity>, source: EntitySource): LibraryEntity {
  return this addEntity LibraryEntity(name, tableId, roots, source) {
    this.excludedRoots = excludedRoots
  }
}

/**
 * [LibraryPropertiesEntity] has the same entity source as [LibraryEntity].
 * [LibraryPropertiesEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addLibraryPropertiesEntity(library: LibraryEntity,
                                                    libraryType: @NonNls String,
                                                    propertiesXmlTag: @NonNls String?): LibraryPropertiesEntity {
  return this addEntity LibraryPropertiesEntity(libraryType, library.entitySource) {
    this.library = library
    this.propertiesXmlTag = propertiesXmlTag
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.getOrCreateExternalSystemModuleOptions(module: ModuleEntity,
                                                                source: EntitySource): ExternalSystemModuleOptionsEntity {
  return module.exModuleOptions ?: run {
    val entity = ExternalSystemModuleOptionsEntity(source) {
      this.module = module
    }
    this.addEntity(entity)
    entity
  }
}

/**
 * This helper function is now obsolete:
 * - If you use this function - inline it
 * - If you want to use this function - use the code inside this function
 */
@Obsolete
fun MutableEntityStorage.addFacetEntity(name: @NlsSafe String,
                                        facetType: @NonNls String,
                                        configurationXmlTag: @NonNls String?,
                                        module: ModuleEntity,
                                        underlyingFacet: FacetEntity?,
                                        source: EntitySource): FacetEntity {
  return this addEntity FacetEntity(name, module.symbolicId, facetType, source) {
    this.configurationXmlTag = configurationXmlTag
    this.module = module
    this.underlyingFacet = underlyingFacet
  }
}

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootPropertiesEntity? = javaSourceRoots.firstOrNull()
fun SourceRootEntity.asJavaResourceRoot(): JavaResourceRootPropertiesEntity? = javaResourceRoots.firstOrNull()

val ModuleEntity.sourceRoots: List<SourceRootEntity>
  get() = contentRoots.flatMap { it.sourceRoots }


fun ModuleEntity.getModuleLibraries(storage: EntityStorage): Sequence<LibraryEntity> {
  return storage.entities(LibraryEntity::class.java)
    .filter { (it.symbolicId.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val EntityStorage.projectLibraries
  get() = entities(LibraryEntity::class.java).filter { it.symbolicId.tableId == LibraryTableId.ProjectLibraryTableId }
