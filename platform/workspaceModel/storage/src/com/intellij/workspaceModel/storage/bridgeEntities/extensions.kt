// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.api.*
import com.intellij.workspaceModel.storage.referrersx
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.*
import kotlin.collections.HashMap

fun WorkspaceEntityStorageBuilder.addModuleEntity(name: String,
                                                  dependencies: List<ModuleDependencyItem>,
                                                  source: EntitySource,
                                                  type: String? = null): ModuleEntity {
  val entity = ModuleEntity {
    this.name = name
    this.type = type
    this.dependencies = dependencies
    this.contentRoots = emptyList()
    this.facets = emptyList()
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
                                                              excludeOutput: Boolean,
                                                              compilerOutput: VirtualFileUrl?,
                                                              compilerOutputForTests: VirtualFileUrl?,
                                                              languageLevelId: String?,
                                                              module: ModuleEntity,
                                                              source: EntitySource): JavaModuleSettingsEntity {
  val entity = JavaModuleSettingsEntity {
    this.inheritedCompilerOutput = inheritedCompilerOutput
    this.excludeOutput = excludeOutput
    this.compilerOutput = compilerOutput
    this.compilerOutputForTests = compilerOutputForTests
    this.languageLevelId = languageLevelId
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addModuleCustomImlDataEntity(rootManagerTagCustomData: String?,
                                                               customModuleOptions: Map<String, String>,
                                                               module: ModuleEntity,
                                                               source: EntitySource): ModuleCustomImlDataEntity {
  val entity = ModuleCustomImlDataEntity {
    this.rootManagerTagCustomData = rootManagerTagCustomData
    this.customModuleOptions = HashMap(customModuleOptions)
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addModuleGroupPathEntity(path: List<String>,
                                                           module: ModuleEntity,
                                                           source: EntitySource): ModuleGroupPathEntity {
  val entity = ModuleGroupPathEntity {
    this.path = path
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addSourceRootEntity(contentRoot: ContentRootEntity,
                                                      url: VirtualFileUrl,
                                                      rootType: String,
                                                      source: EntitySource): SourceRootEntity {
  val entity = SourceRootEntity {
    this.contentRoot = contentRoot
    this.url = url
    this.rootType = rootType
    this.entitySource = source
    this.javaSourceRoots = emptyList()
    this.javaResourceRoots = emptyList()
  }
  this.addEntity(entity)
  return entity
}

/**
 * [JavaSourceRootEntity] has the same entity source as [SourceRootEntity].
 * [JavaSourceRootEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun WorkspaceEntityStorageBuilder.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                          generated: Boolean,
                                                          packagePrefix: String): JavaSourceRootEntity {
  val entity = JavaSourceRootEntity {
    this.sourceRoot = sourceRoot
    this.generated = generated
    this.packagePrefix = packagePrefix
    this.entitySource = sourceRoot.entitySource
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                            generated: Boolean,
                                                            relativeOutputPath: String): JavaResourceRootEntity {
  val entity = JavaResourceRootEntity {
    this.sourceRoot = sourceRoot
    this.generated = generated
    this.relativeOutputPath = relativeOutputPath
    this.entitySource = sourceRoot.entitySource
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                                      propertiesXmlTag: String): CustomSourceRootPropertiesEntity {
  val entity = CustomSourceRootPropertiesEntity {
    this.sourceRoot = sourceRoot
    this.propertiesXmlTag = propertiesXmlTag
    this.entitySource = sourceRoot.entitySource
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addContentRootEntity(url: VirtualFileUrl,
                                                       excludedUrls: List<VirtualFileUrl>,
                                                       excludedPatterns: List<String>,
                                                       module: ModuleEntity): ContentRootEntity {
  return addContentRootEntityWithCustomEntitySource(url, excludedUrls, excludedPatterns, module, module.entitySource)
}

/**
 * Entity source of content root is *almost* the same as the entity source of the corresponding module.
 * Please update assertConsistency in [ContentRootEntityData] if you're using this method.
 */
fun WorkspaceEntityStorageBuilder.addContentRootEntityWithCustomEntitySource(url: VirtualFileUrl,
                                                                             excludedUrls: List<VirtualFileUrl>,
                                                                             excludedPatterns: List<String>,
                                                                             module: ModuleEntity,
                                                                             source: EntitySource): ContentRootEntity {
  val entity = ContentRootEntity {
    this.url = url
    this.excludedUrls = excludedUrls
    this.excludedPatterns = excludedPatterns
    this.module = module
    this.entitySource = source
    this.sourceRoots = emptyList()
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addLibraryEntity(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                   excludedRoots: List<VirtualFileUrl>, source: EntitySource): LibraryEntity {
  val entity = LibraryEntity {
    this.tableId = tableId
    this.name = name
    this.roots = roots
    this.excludedRoots = excludedRoots
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

/**
 * [LibraryPropertiesEntity] has the same entity source as [LibraryEntity].
 * [LibraryPropertiesEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun WorkspaceEntityStorageBuilder.addLibraryPropertiesEntity(library: LibraryEntity,
                                                             libraryType: String,
                                                             propertiesXmlTag: String?): LibraryPropertiesEntity {
  val entity = LibraryPropertiesEntity {
    this.library = library
    this.libraryType = libraryType
    this.propertiesXmlTag = propertiesXmlTag
    this.entitySource = library.entitySource
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addSdkEntity(library: LibraryEntity,
                                               homeUrl: VirtualFileUrl, source: EntitySource): SdkEntity {
  val entity = SdkEntity {
    this.library = library
    this.homeUrl = homeUrl
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.getOrCreateExternalSystemModuleOptions(module: ModuleEntity,
                                                                         source: EntitySource): ExternalSystemModuleOptionsEntity {
  return module.exModuleOptions ?: run {
    val entity = ExternalSystemModuleOptionsEntity {
      this.module = module
      this.entitySource = source
    }
    this.addEntity(entity)
    entity
  }
}

fun WorkspaceEntityStorageBuilder.addFacetEntity(name: String,
                                                 facetType: String,
                                                 configurationXmlTag: String?,
                                                 module: ModuleEntity,
                                                 underlyingFacet: FacetEntity?,
                                                 source: EntitySource): FacetEntity {
  val entity = FacetEntity {
    this.name = name
    this.facetType = facetType
    this.configurationXmlTag = configurationXmlTag
    this.module = module
    this.underlyingFacet = underlyingFacet
    this.moduleId = module.persistentId
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addArtifactEntity(name: String,
                                                    artifactType: String,
                                                    includeInProjectBuild: Boolean,
                                                    outputUrl: VirtualFileUrl?,
                                                    rootElement: CompositePackagingElementEntity,
                                                    source: EntitySource): ArtifactEntity {
  val entity = ArtifactEntity {
    this.name = name
    this.artifactType = artifactType
    this.includeInProjectBuild = includeInProjectBuild
    this.outputUrl = outputUrl
    this.rootElement = rootElement
    this.entitySource = source
    this.customProperties = emptyList()
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addArtifactPropertiesEntity(artifact: ArtifactEntity,
                                                              providerType: String,
                                                              propertiesXmlTag: String?,
                                                              source: EntitySource): ArtifactPropertiesEntity {
  val entity = ArtifactPropertiesEntity {
    this.artifact = artifact
    this.providerType = providerType
    this.propertiesXmlTag = propertiesXmlTag
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addArtifactRootElementEntity(children: List<PackagingElementEntity>,
                                                               source: EntitySource): ArtifactRootElementEntity {
  val entity = ArtifactRootElementEntity {
    this.children = children
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addDirectoryPackagingElementEntity(directoryName: String,
                                                                     children: List<PackagingElementEntity>,
                                                                     source: EntitySource): DirectoryPackagingElementEntity {
  val entity = DirectoryPackagingElementEntity {
    this.directoryName = directoryName
    this.children = children
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addArchivePackagingElementEntity(fileName: String,
                                                                   children: List<PackagingElementEntity>,
                                                                   source: EntitySource): ArchivePackagingElementEntity {
  val entity = ArchivePackagingElementEntity {
    this.fileName = fileName
    this.children = children
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addArtifactOutputPackagingElementEntity(artifact: ArtifactId?,
                                                                          source: EntitySource): ArtifactOutputPackagingElementEntity {
  val entity = ArtifactOutputPackagingElementEntity {
    this.artifact = artifact
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addModuleOutputPackagingElementEntity(module: ModuleId?,
                                                                        source: EntitySource): ModuleOutputPackagingElementEntity {
  val entity = ModuleOutputPackagingElementEntity {
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addLibraryFilesPackagingElementEntity(library: LibraryId?,
                                                                        source: EntitySource): LibraryFilesPackagingElementEntity {
  val entity = LibraryFilesPackagingElementEntity {
    this.library = library
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addModuleSourcePackagingElementEntity(module: ModuleId?,
                                                                        source: EntitySource): ModuleSourcePackagingElementEntity {
  val entity = ModuleSourcePackagingElementEntity {
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addModuleTestOutputPackagingElementEntity(module: ModuleId?,
                                                                            source: EntitySource): ModuleTestOutputPackagingElementEntity {
  val entity = ModuleTestOutputPackagingElementEntity {
    this.module = module
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addDirectoryCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                                         source: EntitySource): DirectoryCopyPackagingElementEntity {
  val entity = DirectoryCopyPackagingElementEntity {
    this.filePath = filePath
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addExtractedDirectoryPackagingElementEntity(filePath: VirtualFileUrl,
                                                                              pathInArchive: String,
                                                                              source: EntitySource): ExtractedDirectoryPackagingElementEntity {
  val entity = ExtractedDirectoryPackagingElementEntity {
    this.filePath = filePath
    this.pathInArchive = pathInArchive
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addFileCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                                    renamedOutputFileName: String?,
                                                                    source: EntitySource): FileCopyPackagingElementEntity {
  val entity = FileCopyPackagingElementEntity {
    this.filePath = filePath
    this.renamedOutputFileName = renamedOutputFileName
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun WorkspaceEntityStorageBuilder.addCustomPackagingElementEntity(typeId: String,
                                                                  propertiesXmlTag: String,
                                                                  children: List<PackagingElementEntity>,
                                                                  source: EntitySource): CustomPackagingElementEntity {
  val entity = CustomPackagingElementEntity {
    this.typeId = typeId
    this.propertiesXmlTag = propertiesXmlTag
    this.children = children
    this.entitySource = source
  }
  this.addEntity(entity)
  return entity
}

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootEntity? = referrersx(JavaSourceRootEntity::sourceRoot).firstOrNull()
fun SourceRootEntity.asJavaResourceRoot() = referrersx(JavaResourceRootEntity::sourceRoot).firstOrNull()
fun SourceRootEntity.asCustomSourceRoot() = referrersx(CustomSourceRootPropertiesEntity::sourceRoot).firstOrNull()
fun LibraryEntity.getCustomProperties() = referrersx(LibraryPropertiesEntity::library).firstOrNull()

val ModuleEntity.sourceRoots: List<SourceRootEntity>
  get() = contentRoots.flatMap { it.sourceRoots }

val FacetEntity.subFacets: List<FacetEntity>
  get() = referrersx(FacetEntity::underlyingFacet)

fun ModuleEntity.getModuleLibraries(storage: WorkspaceEntityStorage): Sequence<LibraryEntity> {
  return storage.entities(LibraryEntity::class.java)
    .filter { (it.persistentId.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val WorkspaceEntityStorage.projectLibraries
  get() = entities(LibraryEntity::class.java).filter { it.persistentId.tableId == LibraryTableId.ProjectLibraryTableId }


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
                                            thisStore: WorkspaceEntityStorage, otherStore: WorkspaceEntityStorage): Boolean {
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
              if (beforeLibrary.excludedRoots != afterLibrary.excludedRoots) false
              else {
                val beforeLibraryKind = beforeLibrary.getCustomProperties()?.libraryType
                val afterLibraryKind = afterLibrary.getCustomProperties()?.libraryType
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