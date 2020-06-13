// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.VirtualFileUrl
import com.intellij.workspaceModel.storage.impl.EntityDataDelegation
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlNullableProperty
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlProperty
import com.intellij.workspaceModel.storage.impl.references.MutableManyToOne
import com.intellij.workspaceModel.storage.impl.references.MutableOneToAbstractMany
import com.intellij.workspaceModel.storage.impl.references.MutableOneToAbstractOneChild
import com.intellij.workspaceModel.storage.impl.references.MutableOneToOneChild

class ModifiableModuleEntity : ModifiableWorkspaceEntityBase<ModuleEntity>() {
  var name: String by EntityDataDelegation()
  var type: String? by EntityDataDelegation()
  var dependencies: List<ModuleDependencyItem> by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleEntity(name: String, dependencies: List<ModuleDependencyItem>, source: EntitySource,
                                                      type: String? = null) = addEntity(
  ModifiableModuleEntity::class.java, source) {
  this.name = name
  this.type = type
  this.dependencies = dependencies
}

class ModifiableJavaModuleSettingsEntity : ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean by EntityDataDelegation()
  var excludeOutput: Boolean by EntityDataDelegation()
  var compilerOutput: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var compilerOutputForTests: VirtualFileUrl? by VirtualFileUrlNullableProperty()

  var module: ModuleEntity by MutableOneToOneChild.NotNull(JavaModuleSettingsEntity::class.java, ModuleEntity::class.java, true)
}

fun WorkspaceEntityStorageDiffBuilder.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
                                                                  excludeOutput: Boolean,
                                                                  compilerOutput: VirtualFileUrl?,
                                                                  compilerOutputForTests: VirtualFileUrl?,
                                                                  module: ModuleEntity,
                                                                  source: EntitySource) = addEntity(
  ModifiableJavaModuleSettingsEntity::class.java, source) {
  this.inheritedCompilerOutput = inheritedCompilerOutput
  this.excludeOutput = excludeOutput
  this.compilerOutput = compilerOutput
  this.compilerOutputForTests = compilerOutputForTests
  this.module = module
}

class ModifiableModuleCustomImlDataEntity : ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? by EntityDataDelegation()
  var customModuleOptions: MutableMap<String, String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleCustomImlDataEntity::class.java, ModuleEntity::class.java, true)
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
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleGroupPathEntity::class.java, ModuleEntity::class.java, true)
}

fun WorkspaceEntityStorageDiffBuilder.addModuleGroupPathEntity(path: List<String>,
                                                               module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableModuleGroupPathEntity::class.java, source) {
  this.path = path
  this.module = module
}

class ModifiableSourceRootEntity : ModifiableWorkspaceEntityBase<SourceRootEntity>() {
  var module: ModuleEntity by MutableManyToOne.NotNull(SourceRootEntity::class.java, ModuleEntity::class.java)
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var tests: Boolean by EntityDataDelegation()
  var rootType: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addSourceRootEntity(module: ModuleEntity,
                                                          url: VirtualFileUrl,
                                                          tests: Boolean,
                                                          rootType: String, source: EntitySource) = addEntity(
  ModifiableSourceRootEntity::class.java, source) {
  this.module = module
  this.url = url
  this.tests = tests
  this.rootType = rootType
}

class ModifiableJavaSourceRootEntity : ModifiableWorkspaceEntityBase<JavaSourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(JavaSourceRootEntity::class.java, SourceRootEntity::class.java)
  var generated: Boolean by EntityDataDelegation()
  var packagePrefix: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                              generated: Boolean,
                                                              packagePrefix: String, source: EntitySource) = addEntity(
  ModifiableJavaSourceRootEntity::class.java, source) {
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
                                                                relativeOutputPath: String, source: EntitySource) = addEntity(
  ModifiableJavaResourceRootEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.relativeOutputPath = relativeOutputPath
}

class ModifiableCustomSourceRootPropertiesEntity : ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(CustomSourceRootPropertiesEntity::class.java, SourceRootEntity::class.java)
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                                          propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomSourceRootPropertiesEntity::class.java, source) {
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
  var roots: List<LibraryRoot> by EntityDataDelegation()
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
  var library: LibraryEntity by MutableOneToOneChild.NotNull(LibraryPropertiesEntity::class.java, LibraryEntity::class.java, true)
  var libraryType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addLibraryPropertiesEntity(library: LibraryEntity,
                                                                 libraryType: String,
                                                                 propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableLibraryPropertiesEntity::class.java, source) {
  this.library = library
  this.libraryType = libraryType
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableSdkEntity : ModifiableWorkspaceEntityBase<SdkEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.NotNull(SdkEntity::class.java, LibraryEntity::class.java, true)
  var homeUrl: VirtualFileUrl by VirtualFileUrlProperty()
}

fun WorkspaceEntityStorageDiffBuilder.addSdkEntity(library: LibraryEntity,
                                                   homeUrl: VirtualFileUrl, source: EntitySource) = addEntity(ModifiableSdkEntity::class.java,
                                                                                                          source) {
  this.library = library
  this.homeUrl = homeUrl
}

class ModifiableExternalSystemModuleOptionsEntity : ModifiableWorkspaceEntityBase<ExternalSystemModuleOptionsEntity>() {
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ExternalSystemModuleOptionsEntity::class.java, ModuleEntity::class.java, true)
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
  var underlyingFacet: FacetEntity? by MutableOneToOneChild.Nullable(FacetEntity::class.java, FacetEntity::class.java, true)
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
  var outputUrl: VirtualFileUrl by VirtualFileUrlProperty()
  var rootElement: CompositePackagingElementEntity by MutableOneToAbstractOneChild(ArtifactEntity::class.java,
                                                                                   CompositePackagingElementEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactEntity(name: String,
                                                        artifactType: String,
                                                        includeInProjectBuild: Boolean,
                                                        outputUrl: VirtualFileUrl,
                                                        rootElement: CompositePackagingElementEntity,
                                                        source: EntitySource) = addEntity(
  ModifiableArtifactEntity::class.java, source) {
  this.name = name
  this.artifactType = artifactType
  this.includeInProjectBuild = includeInProjectBuild
  this.outputUrl = outputUrl
  this.rootElement = rootElement
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

class ModifiableArtifactRootElementEntity : ModifiableWorkspaceEntityBase<ArtifactRootElementEntity>() {
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany(ArtifactRootElementEntity::class.java,
                                                                             PackagingElementEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactRootElementEntity(children: MutableList<PackagingElementEntity>,
                                                                   source: EntitySource) = addEntity(
  ModifiableArtifactRootElementEntity::class.java, source) { this.children = children.asSequence() }

class ModifiableDirectoryPackagingElementEntity : ModifiableWorkspaceEntityBase<DirectoryPackagingElementEntity>() {
  var directoryName: String by EntityDataDelegation()
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany(DirectoryPackagingElementEntity::class.java,
                                                                             PackagingElementEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addDirectoryPackagingElementEntity(directoryName: String,
                                                                         children: MutableList<PackagingElementEntity>,
                                                                         source: EntitySource) = addEntity(
  ModifiableDirectoryPackagingElementEntity::class.java,
  source) { this.directoryName = directoryName; this.children = children.asSequence() }

class ModifiableArchivePackagingElementEntity : ModifiableWorkspaceEntityBase<ArchivePackagingElementEntity>() {
  var fileName: String by EntityDataDelegation()
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany(ArchivePackagingElementEntity::class.java,
                                                                             PackagingElementEntity::class.java)
}

fun WorkspaceEntityStorageDiffBuilder.addArchivePackagingElementEntity(fileName: String,
                                                                       children: MutableList<PackagingElementEntity>,
                                                                       source: EntitySource) = addEntity(
  ModifiableArchivePackagingElementEntity::class.java, source) { this.fileName = fileName; this.children = children.asSequence() }

class ModifiableArtifactOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity>() {
  var artifact: ArtifactId by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addArtifactOutputPackagingElementEntity(artifact: ArtifactId, source: EntitySource) = addEntity(
  ModifiableArtifactOutputPackagingElementEntity::class.java, source) { this.artifact = artifact }

class ModifiableModuleOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleOutputPackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleOutputPackagingElementEntity::class.java, source) { this.module = module }

class ModifiableLibraryFilesPackagingElementEntity : ModifiableWorkspaceEntityBase<LibraryFilesPackagingElementEntity>() {
  var library: LibraryId by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addLibraryFilesPackagingElementEntity(library: LibraryId, source: EntitySource) = addEntity(
  ModifiableLibraryFilesPackagingElementEntity::class.java, source) { this.library = library }

class ModifiableModuleSourcePackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleSourcePackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleSourcePackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleSourcePackagingElementEntity::class.java, source) { this.module = module }

class ModifiableModuleTestOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleTestOutputPackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addModuleTestOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleTestOutputPackagingElementEntity::class.java, source) { this.module = module }

class ModifiableDirectoryCopyPackagingElementEntity : ModifiableWorkspaceEntityBase<DirectoryCopyPackagingElementEntity>() {
  var directory: VirtualFileUrl by VirtualFileUrlProperty()
}

fun WorkspaceEntityStorageDiffBuilder.addDirectoryCopyPackagingElementEntity(directory: VirtualFileUrl, source: EntitySource) = addEntity(
  ModifiableDirectoryCopyPackagingElementEntity::class.java, source) { this.directory = directory }

class ModifiableExtractedDirectoryPackagingElementEntity : ModifiableWorkspaceEntityBase<ExtractedDirectoryPackagingElementEntity>() {
  var archive: VirtualFileUrl by VirtualFileUrlProperty()
  var pathInArchive: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addExtractedDirectoryPackagingElementEntity(archive: VirtualFileUrl,
                                                                                  pathInArchive: String, source: EntitySource) = addEntity(
  ModifiableExtractedDirectoryPackagingElementEntity::class.java, source) {
  this.archive = archive
  this.pathInArchive = pathInArchive
}

class ModifiableFileCopyPackagingElementEntity : ModifiableWorkspaceEntityBase<FileCopyPackagingElementEntity>() {
  var file: VirtualFileUrl by VirtualFileUrlProperty()
  var renamedOutputFileName: String? by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addFileCopyPackagingElementEntity(file: VirtualFileUrl,
                                                                        renamedOutputFileName: String?, source: EntitySource) = addEntity(
  ModifiableFileCopyPackagingElementEntity::class.java, source) {
  this.file = file
  this.renamedOutputFileName = renamedOutputFileName
}

class ModifiableCustomPackagingElementEntity : ModifiableWorkspaceEntityBase<CustomPackagingElementEntity>() {
  var typeId: String by EntityDataDelegation()
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun WorkspaceEntityStorageDiffBuilder.addCustomPackagingElementEntity(typeId: String,
                                                                      propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomPackagingElementEntity::class.java, source) {
  this.typeId = typeId
  this.propertiesXmlTag = propertiesXmlTag
}
