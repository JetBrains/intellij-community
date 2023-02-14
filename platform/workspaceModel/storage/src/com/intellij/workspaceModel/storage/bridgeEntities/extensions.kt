// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls
import java.util.*

fun MutableEntityStorage.addModuleEntity(name: @NlsSafe String,
                                         dependencies: List<ModuleDependencyItem>,
                                         source: EntitySource,
                                         type: @NonNls String? = null): ModuleEntity {
  return this addEntity ModuleEntity(name, dependencies, source) {
    this.type = type
  }
}

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

fun MutableEntityStorage.addModuleCustomImlDataEntity(rootManagerTagCustomData: @NonNls String?,
                                                      customModuleOptions: Map<String, String>,
                                                      module: ModuleEntity,
                                                      source: EntitySource): ModuleCustomImlDataEntity {
  return this addEntity ModuleCustomImlDataEntity(HashMap(customModuleOptions), source) {
    this.rootManagerTagCustomData = rootManagerTagCustomData
    this.module = module
  }
}

fun MutableEntityStorage.addModuleGroupPathEntity(path: List<@NlsSafe String>,
                                                  module: ModuleEntity,
                                                  source: EntitySource): ModuleGroupPathEntity {
  return this addEntity ModuleGroupPathEntity(path, source) {
    this.module = module
  }
}

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
 * [JavaSourceRootEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun MutableEntityStorage.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                 generated: Boolean,
                                                 packagePrefix: @NlsSafe String): JavaSourceRootPropertiesEntity {
  return this addEntity JavaSourceRootPropertiesEntity(generated, packagePrefix, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

fun MutableEntityStorage.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                   generated: Boolean,
                                                   relativeOutputPath: @NlsSafe String): JavaResourceRootPropertiesEntity {
  return this addEntity JavaResourceRootPropertiesEntity(generated, relativeOutputPath, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

fun MutableEntityStorage.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                             propertiesXmlTag: @NonNls String): CustomSourceRootPropertiesEntity {
  return this addEntity CustomSourceRootPropertiesEntity(propertiesXmlTag, sourceRoot.entitySource) {
    this.sourceRoot = sourceRoot
  }
}

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

fun MutableEntityStorage.addLibraryEntity(name: @NlsSafe String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                          excludedRoots: List<VirtualFileUrl>, source: EntitySource): LibraryEntity {
  val excludes = excludedRoots.map { this addEntity ExcludeUrlEntity(it, source) }
  return addLibraryEntityWithExcludes(name, tableId, roots, excludes, source)
}

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
 */
fun MutableEntityStorage.addLibraryPropertiesEntity(library: LibraryEntity,
                                                    libraryType: @NonNls String,
                                                    propertiesXmlTag: @NonNls String?): LibraryPropertiesEntity {
  return this addEntity LibraryPropertiesEntity(libraryType, library.entitySource) {
    this.library = library
    this.propertiesXmlTag = propertiesXmlTag
  }
}

/**
 * Will add an SDK to the project configuration as soon as IDEA-306969, currently these entities are not synchronized with the project model. 
 */
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

fun MutableEntityStorage.addArtifactEntity(name: @NlsSafe String,
                                           artifactType: @NonNls String,
                                           includeInProjectBuild: Boolean,
                                           outputUrl: VirtualFileUrl?,
                                           rootElement: CompositePackagingElementEntity,
                                           source: EntitySource): ArtifactEntity {
  return this addEntity ArtifactEntity(name, artifactType, includeInProjectBuild, source) {
    this.outputUrl = outputUrl
    this.rootElement = rootElement
  }
}

fun MutableEntityStorage.addArtifactPropertiesEntity(artifact: ArtifactEntity,
                                                     providerType: @NonNls String,
                                                     propertiesXmlTag: @NonNls String?,
                                                     source: EntitySource): ArtifactPropertiesEntity {
  return this addEntity ArtifactPropertiesEntity(providerType, source) {
    this.artifact = artifact
    this.propertiesXmlTag = propertiesXmlTag
  }
}

fun MutableEntityStorage.addArtifactRootElementEntity(children: List<PackagingElementEntity>,
                                                      source: EntitySource): ArtifactRootElementEntity {
  return this addEntity ArtifactRootElementEntity(source) {
    this.children = children
  }
}

fun MutableEntityStorage.addDirectoryPackagingElementEntity(directoryName: @NlsSafe String,
                                                            children: List<PackagingElementEntity>,
                                                            source: EntitySource): DirectoryPackagingElementEntity {
  return this addEntity DirectoryPackagingElementEntity(directoryName, source) {
    this.children = children
  }
}

fun MutableEntityStorage.addArchivePackagingElementEntity(fileName: @NlsSafe String,
                                                          children: List<PackagingElementEntity>,
                                                          source: EntitySource): ArchivePackagingElementEntity {
  return this addEntity ArchivePackagingElementEntity(fileName, source) {
    this.children = children
  }
}

fun MutableEntityStorage.addArtifactOutputPackagingElementEntity(artifact: ArtifactId?,
                                                                 source: EntitySource): ArtifactOutputPackagingElementEntity {
  return this addEntity ArtifactOutputPackagingElementEntity(source) {
    this.artifact = artifact
  }
}

fun MutableEntityStorage.addModuleOutputPackagingElementEntity(module: ModuleId?,
                                                               source: EntitySource): ModuleOutputPackagingElementEntity {
  return this addEntity ModuleOutputPackagingElementEntity(source) {
    this.module = module
  }
}

fun MutableEntityStorage.addLibraryFilesPackagingElementEntity(library: LibraryId?,
                                                               source: EntitySource): LibraryFilesPackagingElementEntity {
  return this addEntity LibraryFilesPackagingElementEntity(source) {
    this.library = library
  }
}

fun MutableEntityStorage.addModuleSourcePackagingElementEntity(module: ModuleId?,
                                                               source: EntitySource): ModuleSourcePackagingElementEntity {
  return this addEntity ModuleSourcePackagingElementEntity(source) {
    this.module = module
  }
}

fun MutableEntityStorage.addModuleTestOutputPackagingElementEntity(module: ModuleId?,
                                                                   source: EntitySource): ModuleTestOutputPackagingElementEntity {
  return this addEntity ModuleTestOutputPackagingElementEntity(source) {
    this.module = module
  }
}

fun MutableEntityStorage.addDirectoryCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                                source: EntitySource): DirectoryCopyPackagingElementEntity {
  return this addEntity DirectoryCopyPackagingElementEntity(filePath, source)
}

fun MutableEntityStorage.addExtractedDirectoryPackagingElementEntity(filePath: VirtualFileUrl,
                                                                     pathInArchive: @NlsSafe String,
                                                                     source: EntitySource): ExtractedDirectoryPackagingElementEntity {
  return this addEntity ExtractedDirectoryPackagingElementEntity(filePath, pathInArchive, source)
}

fun MutableEntityStorage.addFileCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                           renamedOutputFileName: @NlsSafe String?,
                                                           source: EntitySource): FileCopyPackagingElementEntity {
  return this addEntity FileCopyPackagingElementEntity(filePath, source) {
    this.renamedOutputFileName = renamedOutputFileName
  }
}

fun MutableEntityStorage.addCustomPackagingElementEntity(typeId: @NonNls String,
                                                         propertiesXmlTag: @NonNls String,
                                                         children: List<PackagingElementEntity>,
                                                         source: EntitySource): CustomPackagingElementEntity {
  return this addEntity CustomPackagingElementEntity(typeId, propertiesXmlTag, source) {
    this.children = children
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