// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.*

fun MutableEntityStorage.addModuleEntity(name: String,
                                         dependencies: List<ModuleDependencyItem>,
                                         source: EntitySource,
                                         type: String? = null): ModuleEntity {
  val entity = ModuleEntity(name, dependencies, source) {
    this.type = type
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
                                                     excludeOutput: Boolean,
                                                     compilerOutput: VirtualFileUrl?,
                                                     compilerOutputForTests: VirtualFileUrl?,
                                                     languageLevelId: String?,
                                                     module: ModuleEntity,
                                                     source: EntitySource): JavaModuleSettingsEntity {
  val entity = JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, source) {
    this.compilerOutput = compilerOutput
    this.compilerOutputForTests = compilerOutputForTests
    this.languageLevelId = languageLevelId
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addModuleCustomImlDataEntity(rootManagerTagCustomData: String?,
                                                      customModuleOptions: Map<String, String>,
                                                      module: ModuleEntity,
                                                      source: EntitySource): ModuleCustomImlDataEntity {
  val entity = ModuleCustomImlDataEntity(HashMap(customModuleOptions), source) {
    this.rootManagerTagCustomData = rootManagerTagCustomData
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addModuleGroupPathEntity(path: List<String>,
                                                  module: ModuleEntity,
                                                  source: EntitySource): ModuleGroupPathEntity {
  val entity = ModuleGroupPathEntity(path, source) {
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSourceRootEntity(contentRoot: ContentRootEntity,
                                             url: VirtualFileUrl,
                                             rootType: String,
                                             source: EntitySource): SourceRootEntity {
  val entity = SourceRootEntity(url, rootType, source) {
    this.contentRoot = contentRoot
  }
  this.addEntity(entity)
  return entity
}

/**
 * [JavaSourceRootPropertiesEntity] has the same entity source as [SourceRootEntity].
 * [JavaSourceRootEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun MutableEntityStorage.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                 generated: Boolean,
                                                 packagePrefix: String): JavaSourceRootPropertiesEntity {
  val entity = JavaSourceRootPropertiesEntity(generated, packagePrefix, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                   generated: Boolean,
                                                   relativeOutputPath: String): JavaResourceRootPropertiesEntity {
  val entity = JavaResourceRootPropertiesEntity(generated, relativeOutputPath, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                             propertiesXmlTag: String): CustomSourceRootPropertiesEntity {
  val entity = CustomSourceRootPropertiesEntity(propertiesXmlTag, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addContentRootEntity(url: VirtualFileUrl,
                                              excludedUrls: List<VirtualFileUrl>,
                                              excludedPatterns: List<String>,
                                              module: ModuleEntity,
                                              source: EntitySource = module.entitySource): ContentRootEntity {
  val excludes = excludedUrls.map { this addEntity ExcludeUrlEntity(it, source) }
  return this addEntity ContentRootEntity(url, excludedPatterns, source) {
    this.excludedUrls = excludes
    this.module = module
  }
}

fun MutableEntityStorage.addLibraryEntity(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                          excludedRoots: List<VirtualFileUrl>, source: EntitySource): LibraryEntity {
  val excludes = excludedRoots.map { this addEntity ExcludeUrlEntity(it, source) }
  return addLibraryEntityWithExcludes(name, tableId, roots, excludes, source)
}

fun MutableEntityStorage.addLibraryEntityWithExcludes(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                      excludedRoots: List<ExcludeUrlEntity>, source: EntitySource): LibraryEntity {
  return this addEntity LibraryEntity(name, tableId, roots, source) {
    this.excludedRoots = excludedRoots
  }
}

/**
 * [LibraryPropertiesEntity] has the same entity source as [LibraryEntity].
 * [LibraryPropertiesEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun MutableEntityStorage.addLibraryPropertiesEntity(library: LibraryEntity,
                                                    libraryType: String,
                                                    propertiesXmlTag: String?): LibraryPropertiesEntity {
  val entity = LibraryPropertiesEntity(libraryType, library.entitySource) {
    this.library = library
    this.propertiesXmlTag = propertiesXmlTag
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addSdkEntity(library: LibraryEntity,
                                      homeUrl: VirtualFileUrl, source: EntitySource): SdkEntity {
  val entity = SdkEntity(homeUrl, source) {
    this.library = library
  }
  this.addEntity(entity)
  return entity
}

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

fun MutableEntityStorage.addFacetEntity(name: String,
                                        facetType: String,
                                        configurationXmlTag: String?,
                                        module: ModuleEntity,
                                        underlyingFacet: FacetEntity?,
                                        source: EntitySource): FacetEntity {
  val entity = FacetEntity(name, facetType, module.symbolicId, source) {
    this.configurationXmlTag = configurationXmlTag
    this.module = module
    this.underlyingFacet = underlyingFacet
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addArtifactEntity(name: String,
                                           artifactType: String,
                                           includeInProjectBuild: Boolean,
                                           outputUrl: VirtualFileUrl?,
                                           rootElement: CompositePackagingElementEntity,
                                           source: EntitySource): ArtifactEntity {
  val entity = ArtifactEntity(name, artifactType, includeInProjectBuild, source) {
    this.outputUrl = outputUrl
    this.rootElement = rootElement
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addArtifactPropertiesEntity(artifact: ArtifactEntity,
                                                     providerType: String,
                                                     propertiesXmlTag: String?,
                                                     source: EntitySource): ArtifactPropertiesEntity {
  val entity = ArtifactPropertiesEntity(providerType, source) {
    this.artifact = artifact
    this.propertiesXmlTag = propertiesXmlTag
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addArtifactRootElementEntity(children: List<PackagingElementEntity>,
                                                      source: EntitySource): ArtifactRootElementEntity {
  val entity = ArtifactRootElementEntity(source) {
    this.children = children
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addDirectoryPackagingElementEntity(directoryName: String,
                                                            children: List<PackagingElementEntity>,
                                                            source: EntitySource): DirectoryPackagingElementEntity {
  val entity = DirectoryPackagingElementEntity(directoryName, source) {
    this.children = children
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addArchivePackagingElementEntity(fileName: String,
                                                          children: List<PackagingElementEntity>,
                                                          source: EntitySource): ArchivePackagingElementEntity {
  val entity = ArchivePackagingElementEntity(fileName, source) {
    this.children = children
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addArtifactOutputPackagingElementEntity(artifact: ArtifactId?,
                                                                 source: EntitySource): ArtifactOutputPackagingElementEntity {
  val entity = ArtifactOutputPackagingElementEntity(source) {
    this.artifact = artifact
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addModuleOutputPackagingElementEntity(module: ModuleId?,
                                                               source: EntitySource): ModuleOutputPackagingElementEntity {
  val entity = ModuleOutputPackagingElementEntity(source) {
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addLibraryFilesPackagingElementEntity(library: LibraryId?,
                                                               source: EntitySource): LibraryFilesPackagingElementEntity {
  val entity = LibraryFilesPackagingElementEntity(source) {
    this.library = library
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addModuleSourcePackagingElementEntity(module: ModuleId?,
                                                               source: EntitySource): ModuleSourcePackagingElementEntity {
  val entity = ModuleSourcePackagingElementEntity(source) {
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addModuleTestOutputPackagingElementEntity(module: ModuleId?,
                                                                   source: EntitySource): ModuleTestOutputPackagingElementEntity {
  val entity = ModuleTestOutputPackagingElementEntity(source) {
    this.module = module
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addDirectoryCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                                source: EntitySource): DirectoryCopyPackagingElementEntity {
  val entity = DirectoryCopyPackagingElementEntity(filePath, source)
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addExtractedDirectoryPackagingElementEntity(filePath: VirtualFileUrl,
                                                                     pathInArchive: String,
                                                                     source: EntitySource): ExtractedDirectoryPackagingElementEntity {
  val entity = ExtractedDirectoryPackagingElementEntity(filePath, pathInArchive, source)
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addFileCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                           renamedOutputFileName: String?,
                                                           source: EntitySource): FileCopyPackagingElementEntity {
  val entity = FileCopyPackagingElementEntity(filePath, source) {
    this.renamedOutputFileName = renamedOutputFileName
  }
  this.addEntity(entity)
  return entity
}

fun MutableEntityStorage.addCustomPackagingElementEntity(typeId: String,
                                                         propertiesXmlTag: String,
                                                         children: List<PackagingElementEntity>,
                                                         source: EntitySource): CustomPackagingElementEntity {
  val entity = CustomPackagingElementEntity(typeId, propertiesXmlTag, source) {
    this.children = children
  }
  this.addEntity(entity)
  return entity
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


/**
 * All the [equalsAsOrderEntry] methods work similar to [compareTo] methods of corresponding order entries in the
 *   legacy project model.
 */

fun SourceRootEntity.equalsAsOrderEntry(other: SourceRootEntity): Boolean {
  val beforePackagePrefix = this.asJavaSourceRoot()?.packagePrefix ?: this.asJavaResourceRoot()?.relativeOutputPath
  val afterPackagePrefix = other.asJavaSourceRoot()?.packagePrefix ?: other.asJavaResourceRoot()?.relativeOutputPath
  if (beforePackagePrefix != afterPackagePrefix) return false

  val beforeGenerated = this.asJavaSourceRoot()?.generated ?: this.asJavaResourceRoot()?.generated
  val afterGenerated = other.asJavaSourceRoot()?.generated ?: other.asJavaResourceRoot()?.generated
  if (beforeGenerated != afterGenerated) return false

  if (this.url != other.url) return false

  return true
}

fun SourceRootEntity.hashCodeAsOrderEntry(): Int {
  val packagePrefix = this.asJavaSourceRoot()?.packagePrefix ?: this.asJavaResourceRoot()?.relativeOutputPath
  val generated = this.asJavaSourceRoot()?.generated ?: this.asJavaResourceRoot()?.generated

  return Objects.hash(packagePrefix, generated, url)
}

fun ContentRootEntity.equalsAsOrderEntry(other: ContentRootEntity): Boolean {
  if (this.url != other.url) return false
  if (this.excludedUrls != other.excludedUrls) return false
  if (this.excludedPatterns != other.excludedPatterns) return false
  return true
}

fun ContentRootEntity.hashCodeAsOrderEntry(): Int = Objects.hash(url, excludedUrls, excludedPatterns)

fun ModuleDependencyItem.equalsAsOrderEntry(other: ModuleDependencyItem,
                                            thisStore: EntityStorage, otherStore: EntityStorage): Boolean {
  if (this::class != other::class) return false
  return when (this) {
    is ModuleDependencyItem.InheritedSdkDependency -> true  // This is object
    is ModuleDependencyItem.ModuleSourceDependency -> true  // This is object
    is ModuleDependencyItem.SdkDependency -> {
      other as ModuleDependencyItem.SdkDependency
      sdkName == other.sdkName
    }
    is ModuleDependencyItem.Exportable -> {
      other as ModuleDependencyItem.Exportable
      when {
        exported != other.exported -> false
        scope != other.scope -> false
        else -> when (this) {
          is ModuleDependencyItem.Exportable.ModuleDependency -> {
            other as ModuleDependencyItem.Exportable.ModuleDependency
            when {
              productionOnTest != other.productionOnTest -> false
              module.name != other.module.name -> false
              else -> true
            }
          }

          is ModuleDependencyItem.Exportable.LibraryDependency -> {
            other as ModuleDependencyItem.Exportable.LibraryDependency
            if (library.name != other.library.name) false
            else if (library.tableId.level != other.library.tableId.level) false
            else {
              val beforeLibrary = thisStore.resolve(library)!!
              val afterLibrary = otherStore.resolve(other.library)!!
              if (beforeLibrary.excludedRoots.map { it.url } != afterLibrary.excludedRoots.map { it.url }) false
              else {
                val beforeLibraryKind = beforeLibrary.libraryProperties?.libraryType
                val afterLibraryKind = afterLibrary.libraryProperties?.libraryType
                when {
                  beforeLibraryKind != afterLibraryKind -> false
                  beforeLibrary.roots != afterLibrary.roots -> false
                  else -> true
                }
              }
            }
          }
        }
      }
    }
  }
}