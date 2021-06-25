// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.ModuleDependencyEntityDataDelegation
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlLibraryRootProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlNullableProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlProperty
import com.intellij.workspaceModel.storage.impl.references.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

private val LOG = logger<WorkspaceEntityStorage>()

class ModifiableModuleEntity : ModifiableWorkspaceEntityBase<ModuleEntity>() {
  internal var dependencyChanged = false
  var name: String by EntityDataDelegation()
  var type: String? by EntityDataDelegation()
  var dependencies: List<ModuleDependencyItem> by ModuleDependencyEntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleEntity(name: String, dependencies: List<ModuleDependencyItem>, source: EntitySource,
                                                      type: String? = null): ModuleEntity {
  LOG.debug { "Add moduleEntity: $name" }
  return addEntity(ModifiableModuleEntity::class.java, source) {
    this.name = name
    this.type = type
    this.dependencies = dependencies
  }
}

class ModifiableJavaModuleSettingsEntity : ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean by EntityDataDelegation()
  var excludeOutput: Boolean by EntityDataDelegation()
  var compilerOutput: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var compilerOutputForTests: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var languageLevelId: String? by EntityDataDelegation()

  var module: ModuleEntity by MutableOneToOneChild.NotNull(JavaModuleSettingsEntity::class.java, ModuleEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
                                                                  excludeOutput: Boolean,
                                                                  compilerOutput: VirtualFileUrl?,
                                                                  compilerOutputForTests: VirtualFileUrl?,
                                                                  languageLevelId: String?,
                                                                  module: ModuleEntity,
                                                                  source: EntitySource) = addEntity(
  ModifiableJavaModuleSettingsEntity::class.java, source) {
  this.inheritedCompilerOutput = inheritedCompilerOutput
  this.excludeOutput = excludeOutput
  this.compilerOutput = compilerOutput
  this.compilerOutputForTests = compilerOutputForTests
  this.languageLevelId = languageLevelId
  this.module = module
}

class ModifiableModuleCustomImlDataEntity : ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? by EntityDataDelegation()
  var customModuleOptions: MutableMap<String, String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleCustomImlDataEntity::class.java, ModuleEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addModuleCustomImlDataEntity(rootManagerTagCustomData: String?,
                                                                   customModuleOptions: Map<String, String>,
                                                                   module: ModuleEntity,
                                                                   source: EntitySource) = addEntity(
  ModifiableModuleCustomImlDataEntity::class.java, source) {
  this.rootManagerTagCustomData = rootManagerTagCustomData
  this.customModuleOptions = HashMap(customModuleOptions)
  this.module = module
}

class ModifiableModuleGroupPathEntity : ModifiableWorkspaceEntityBase<ModuleGroupPathEntity>() {
  var path: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleGroupPathEntity::class.java, ModuleEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addModuleGroupPathEntity(path: List<String>,
                                                               module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableModuleGroupPathEntity::class.java, source) {
  this.path = path
  this.module = module
}

class ModifiableSourceRootEntity : ModifiableWorkspaceEntityBase<SourceRootEntity>() {
  var contentRoot: ContentRootEntity by MutableManyToOne.NotNull(SourceRootEntity::class.java, ContentRootEntity::class.java)
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var rootType: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addSourceRootEntity(contentRoot: ContentRootEntity,
                                                          url: VirtualFileUrl,
                                                          rootType: String, source: EntitySource) = addEntity(
  ModifiableSourceRootEntity::class.java, source) {
  this.contentRoot = contentRoot
  this.url = url
  this.rootType = rootType
}

class ModifiableJavaSourceRootEntity : ModifiableWorkspaceEntityBase<JavaSourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(JavaSourceRootEntity::class.java, SourceRootEntity::class.java)
  var generated: Boolean by EntityDataDelegation()
  var packagePrefix: String by EntityDataDelegation()
}

/**
 * [JavaSourceRootEntity] has the same entity source as [SourceRootEntity].
 * [JavaSourceRootEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun WorkspaceEntityStorageDiffBuilder.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                              generated: Boolean,
                                                              packagePrefix: String) = addEntity(
  ModifiableJavaSourceRootEntity::class.java, sourceRoot.entitySource) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.packagePrefix = packagePrefix
}

class ModifiableJavaResourceRootEntity : ModifiableWorkspaceEntityBase<JavaResourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(JavaResourceRootEntity::class.java, SourceRootEntity::class.java)
  var generated: Boolean by EntityDataDelegation()
  var relativeOutputPath: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                                generated: Boolean,
                                                                relativeOutputPath: String) = addEntity(
  ModifiableJavaResourceRootEntity::class.java, sourceRoot.entitySource) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.relativeOutputPath = relativeOutputPath
}

class ModifiableCustomSourceRootPropertiesEntity : ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(CustomSourceRootPropertiesEntity::class.java, SourceRootEntity::class.java)
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity, propertiesXmlTag: String) = addEntity(
  ModifiableCustomSourceRootPropertiesEntity::class.java, sourceRoot.entitySource) {
  this.sourceRoot = sourceRoot
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableContentRootEntity : ModifiableWorkspaceEntityBase<ContentRootEntity>() {
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var excludedUrls: List<VirtualFileUrl> by VirtualFileUrlListProperty()
  var excludedPatterns: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableManyToOne.NotNull(ContentRootEntity::class.java, ModuleEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addContentRootEntity(url: VirtualFileUrl,
                                                           excludedUrls: List<VirtualFileUrl>,
                                                           excludedPatterns: List<String>,
                                                           module: ModuleEntity): ContentRootEntity {
  return addContentRootEntityWithCustomEntitySource(url, excludedUrls, excludedPatterns, module, module.entitySource)
}

/**
 * Entity source of content root is *almost* the same as the entity source of the corresponding module.
 * Please update assertConsistency in [ContentRootEntityData] if you're using this method.
 */
fun WorkspaceEntityStorageDiffBuilder.addContentRootEntityWithCustomEntitySource(url: VirtualFileUrl,
                                                                                 excludedUrls: List<VirtualFileUrl>,
                                                                                 excludedPatterns: List<String>,
                                                                                 module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableContentRootEntity::class.java, source) {
  this.url = url
  this.excludedUrls = excludedUrls
  this.excludedPatterns = excludedPatterns
  this.module = module
}

class ModifiableLibraryEntity : ModifiableWorkspaceEntityBase<LibraryEntity>() {
  var tableId: LibraryTableId by EntityDataDelegation()
  var name: String by EntityDataDelegation()
  var roots: List<LibraryRoot> by VirtualFileUrlLibraryRootProperty()
  var excludedRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

fun WorkspaceEntityStorageDiffBuilder.addLibraryEntity(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                       excludedRoots: List<VirtualFileUrl>, source: EntitySource) = addEntity(
  ModifiableLibraryEntity::class.java, source) {
  this.tableId = tableId
  this.name = name
  this.roots = roots
  this.excludedRoots = excludedRoots
}

class ModifiableLibraryPropertiesEntity : ModifiableWorkspaceEntityBase<LibraryPropertiesEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.NotNull(LibraryPropertiesEntity::class.java, LibraryEntity::class.java)
  var libraryType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

/**
 * [LibraryPropertiesEntity] has the same entity source as [LibraryEntity].
 * [LibraryPropertiesEntityData] contains assertion for that. Please update an assertion in case you need a different entity source for these
 *   entities.
 */
fun WorkspaceEntityStorageDiffBuilder.addLibraryPropertiesEntity(library: LibraryEntity,
                                                                 libraryType: String,
                                                                 propertiesXmlTag: String?) = addEntity(
  ModifiableLibraryPropertiesEntity::class.java, library.entitySource) {
  this.library = library
  this.libraryType = libraryType
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableSdkEntity : ModifiableWorkspaceEntityBase<SdkEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.NotNull(SdkEntity::class.java, LibraryEntity::class.java)
  var homeUrl: VirtualFileUrl by VirtualFileUrlProperty()
}

fun WorkspaceEntityStorageDiffBuilder.addSdkEntity(library: LibraryEntity,
                                                   homeUrl: VirtualFileUrl, source: EntitySource) = addEntity(ModifiableSdkEntity::class.java,
                                                                                                              source) {
  this.library = library
  this.homeUrl = homeUrl
}

class ModifiableExternalSystemModuleOptionsEntity : ModifiableWorkspaceEntityBase<ExternalSystemModuleOptionsEntity>() {
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ExternalSystemModuleOptionsEntity::class.java, ModuleEntity::class.java)
  var externalSystem: String? by EntityDataDelegation()
  var externalSystemModuleVersion: String? by EntityDataDelegation()

  var linkedProjectPath: String? by EntityDataDelegation()
  var linkedProjectId: String? by EntityDataDelegation()
  var rootProjectPath: String? by EntityDataDelegation()

  var externalSystemModuleGroup: String? by EntityDataDelegation()
  var externalSystemModuleType: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.getOrCreateExternalSystemModuleOptions(module: ModuleEntity,
                                                                             source: EntitySource): ExternalSystemModuleOptionsEntity =
  module.externalSystemOptions ?: addEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, source) {
    this.module = module
  }

class ModifiableFacetEntity : ModifiableWorkspaceEntityBase<FacetEntity>() {
  var name: String by EntityDataDelegation()
  var facetType: String by EntityDataDelegation()
  var configurationXmlTag: String? by EntityDataDelegation()
  var moduleId: ModuleId by EntityDataDelegation()

  var module: ModuleEntity by MutableManyToOne.NotNull(FacetEntity::class.java, ModuleEntity::class.java)
  var underlyingFacet: FacetEntity? by MutableManyToOne.Nullable(FacetEntity::class.java, FacetEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addFacetEntity(name: String, facetType: String, configurationXmlTag: String?, module: ModuleEntity,
                                                     underlyingFacet: FacetEntity?, source: EntitySource) =
  addEntity(ModifiableFacetEntity::class.java, source) {
    this.name = name
    this.facetType = facetType
    this.configurationXmlTag = configurationXmlTag
    this.module = module
    this.underlyingFacet = underlyingFacet
    this.moduleId = module.persistentId()
  }

class ModifiableArtifactEntity : ModifiableWorkspaceEntityBase<ArtifactEntity>() {
  var name: String by EntityDataDelegation()
  var artifactType: String by EntityDataDelegation()
  var includeInProjectBuild: Boolean by EntityDataDelegation()
  var outputUrl: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var rootElement: CompositePackagingElementEntity? by MutableOneToAbstractOneParent(ArtifactEntity::class.java,
                                                                                     CompositePackagingElementEntity::class.java)
  var customProperties: Sequence<ArtifactPropertiesEntity> by customPropertiesDelegate

  companion object {
    val customPropertiesDelegate = MutableOneToMany<ArtifactEntity, ArtifactPropertiesEntity, ModifiableArtifactEntity>(ArtifactEntity::class.java, ArtifactPropertiesEntity::class.java, false)
  }
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactEntity(name: String,
                                                        artifactType: String,
                                                        includeInProjectBuild: Boolean,
                                                        outputUrl: VirtualFileUrl?,
                                                        rootElement: CompositePackagingElementEntity,
                                                        source: EntitySource): ArtifactEntity {
  return addEntity(ModifiableArtifactEntity::class.java, source) {
    this.name = name
    this.artifactType = artifactType
    this.includeInProjectBuild = includeInProjectBuild
    this.outputUrl = outputUrl
    this.rootElement = rootElement
  }
}

class ModifiableArtifactPropertiesEntity : ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity>() {
  var artifact: ArtifactEntity by MutableManyToOne.NotNull(ArtifactPropertiesEntity::class.java, ArtifactEntity::class.java)
  var providerType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactPropertiesEntity(artifact: ArtifactEntity,
                                                                  providerType: String,
                                                                  propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableArtifactPropertiesEntity::class.java, source) {
  this.artifact = artifact
  this.providerType = providerType
  this.propertiesXmlTag = propertiesXmlTag
}

abstract class ModifiableCompositePackagingElementEntity<T: CompositePackagingElementEntity>(clazz: Class<T>) : ModifiableWorkspaceEntityBase<T>() {
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany(clazz, PackagingElementEntity::class.java)
}

class ModifiableArtifactRootElementEntity : ModifiableCompositePackagingElementEntity<ArtifactRootElementEntity>(
  ArtifactRootElementEntity::class.java
)

fun WorkspaceEntityStorageDiffBuilder.addArtifactRootElementEntity(children: List<PackagingElementEntity>,
                                                                   source: EntitySource): ArtifactRootElementEntity {
  return addEntity(ModifiableArtifactRootElementEntity::class.java, source) {
    this.children = children.asSequence()
  }
}

class ModifiableDirectoryPackagingElementEntity : ModifiableCompositePackagingElementEntity<DirectoryPackagingElementEntity>(
  DirectoryPackagingElementEntity::class.java) {
  var directoryName: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addDirectoryPackagingElementEntity(directoryName: String,
                                                                         children: List<PackagingElementEntity>,
                                                                         source: EntitySource): DirectoryPackagingElementEntity {
  return addEntity(ModifiableDirectoryPackagingElementEntity::class.java, source) {
    this.directoryName = directoryName
    this.children = children.asSequence()
  }
}

class ModifiableArchivePackagingElementEntity : ModifiableCompositePackagingElementEntity<ArchivePackagingElementEntity>(
  ArchivePackagingElementEntity::class.java) {
  var fileName: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addArchivePackagingElementEntity(fileName: String,
                                                                       children: List<PackagingElementEntity>,
                                                                       source: EntitySource): ArchivePackagingElementEntity {
  return addEntity(ModifiableArchivePackagingElementEntity::class.java, source) {
    this.fileName = fileName
    this.children = children.asSequence()
  }
}

class ModifiableArtifactOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity>() {
  var artifact: ArtifactId? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactOutputPackagingElementEntity(artifact: ArtifactId?, source: EntitySource): ArtifactOutputPackagingElementEntity {
  return addEntity(ModifiableArtifactOutputPackagingElementEntity::class.java, source) {
    this.artifact = artifact
  }
}

class ModifiableModuleOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleOutputPackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleOutputPackagingElementEntity(module: ModuleId?, source: EntitySource): ModuleOutputPackagingElementEntity {
  return addEntity(ModifiableModuleOutputPackagingElementEntity::class.java, source) {
    this.module = module
  }
}

class ModifiableLibraryFilesPackagingElementEntity : ModifiableWorkspaceEntityBase<LibraryFilesPackagingElementEntity>() {
  var library: LibraryId? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addLibraryFilesPackagingElementEntity(library: LibraryId?, source: EntitySource): LibraryFilesPackagingElementEntity {
  return addEntity(ModifiableLibraryFilesPackagingElementEntity::class.java, source) {
    this.library = library
  }
}

class ModifiableModuleSourcePackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleSourcePackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleSourcePackagingElementEntity(module: ModuleId?, source: EntitySource): ModuleSourcePackagingElementEntity {
  return addEntity(ModifiableModuleSourcePackagingElementEntity::class.java, source) {
    this.module = module
  }
}

class ModifiableModuleTestOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleTestOutputPackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleTestOutputPackagingElementEntity(module: ModuleId?, source: EntitySource): ModuleTestOutputPackagingElementEntity {
  return addEntity(ModifiableModuleTestOutputPackagingElementEntity::class.java, source) {
    this.module = module
  }
}

abstract class ModifiableFileOrDirectoryPackagingElement<T : FileOrDirectoryPackagingElementEntity> : ModifiableWorkspaceEntityBase<T>() {
  var filePath: VirtualFileUrl by VirtualFileUrlProperty()
}

class ModifiableDirectoryCopyPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<DirectoryCopyPackagingElementEntity>()

fun WorkspaceEntityStorageDiffBuilder.addDirectoryCopyPackagingElementEntity(filePath: VirtualFileUrl, source: EntitySource): DirectoryCopyPackagingElementEntity {
  return addEntity(ModifiableDirectoryCopyPackagingElementEntity::class.java, source) {
    this.filePath = filePath
  }
}

class ModifiableExtractedDirectoryPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<ExtractedDirectoryPackagingElementEntity>() {
  var pathInArchive: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addExtractedDirectoryPackagingElementEntity(filePath: VirtualFileUrl,
                                                                                  pathInArchive: String,
                                                                                  source: EntitySource): ExtractedDirectoryPackagingElementEntity {
  return addEntity(ModifiableExtractedDirectoryPackagingElementEntity::class.java, source) {
    this.filePath = filePath
    this.pathInArchive = pathInArchive
  }
}

class ModifiableFileCopyPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<FileCopyPackagingElementEntity>() {
  var renamedOutputFileName: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addFileCopyPackagingElementEntity(filePath: VirtualFileUrl,
                                                                        renamedOutputFileName: String?,
                                                                        source: EntitySource): FileCopyPackagingElementEntity {
  return addEntity(ModifiableFileCopyPackagingElementEntity::class.java, source) {
    this.filePath = filePath
    this.renamedOutputFileName = renamedOutputFileName
  }
}

class ModifiableCustomPackagingElementEntity : ModifiableCompositePackagingElementEntity<CustomPackagingElementEntity>(CustomPackagingElementEntity::class.java) {
  var typeId: String by EntityDataDelegation()
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addCustomPackagingElementEntity(typeId: String,
                                                                      propertiesXmlTag: String,
                                                                      children: List<PackagingElementEntity>,
                                                                      source: EntitySource): CustomPackagingElementEntity {
  return addEntity(ModifiableCustomPackagingElementEntity::class.java, source) {
    this.typeId = typeId
    this.propertiesXmlTag = propertiesXmlTag
    this.children = children.asSequence()
  }
}
