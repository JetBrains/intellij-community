package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.EntityDataDelegation
import com.intellij.workspace.api.pstorage.PModifiableTypedEntity
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlListProperty
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlNullableProperty
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlProperty
import com.intellij.workspace.api.pstorage.references.MutableManyToOne
import com.intellij.workspace.api.pstorage.references.MutableOneToAbstractMany
import com.intellij.workspace.api.pstorage.references.MutableOneToAbstractOneChild
import com.intellij.workspace.api.pstorage.references.MutableOneToOneChild

class ModifiableModuleEntity : PModifiableTypedEntity<ModuleEntity>() {
  var name: String by EntityDataDelegation()
  var type: String? by EntityDataDelegation()
  var dependencies: List<ModuleDependencyItem> by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addModuleEntity(name: String, dependencies: List<ModuleDependencyItem>, source: EntitySource,
                                                  type : String? = null) = addEntity(
  ModifiableModuleEntity::class.java, source) {
  this.name = name
  this.type = type
  this.dependencies = dependencies
}

class ModifiableJavaModuleSettingsEntity : PModifiableTypedEntity<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean by EntityDataDelegation()
  var excludeOutput: Boolean by EntityDataDelegation()
  var compilerOutput: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var compilerOutputForTests: VirtualFileUrl? by VirtualFileUrlNullableProperty()

  var module: ModuleEntity by MutableOneToOneChild.HardRef.NotNull(JavaModuleSettingsEntity::class, ModuleEntity::class, true)
}

fun TypedEntityStorageDiffBuilder.addJavaModuleSettingsEntity(inheritedCompilerOutput: Boolean,
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

class ModifiableModuleCustomImlDataEntity : PModifiableTypedEntity<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? by EntityDataDelegation()
  var customModuleOptions: MutableMap<String, String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.HardRef.NotNull(ModuleCustomImlDataEntity::class, ModuleEntity::class, true)
}

fun TypedEntityStorageDiffBuilder.addModuleCustomImlDataEntity(rootManagerTagCustomData: String?,
                                                               customModuleOptions: Map<String, String>,
                                                               module: ModuleEntity,
                                                               source: EntitySource) = addEntity(
  ModifiableModuleCustomImlDataEntity::class.java, source) {
  this.rootManagerTagCustomData = rootManagerTagCustomData
  this.customModuleOptions = HashMap(customModuleOptions)
  this.module = module
}

class ModifiableModuleGroupPathEntity : PModifiableTypedEntity<ModuleGroupPathEntity>() {
  var path: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.HardRef.NotNull(ModuleGroupPathEntity::class, ModuleEntity::class, true)
}

fun TypedEntityStorageDiffBuilder.addModuleGroupPathEntity(path: List<String>,
                                                           module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableModuleGroupPathEntity::class.java, source) {
  this.path = path
  this.module = module
}

class ModifiableSourceRootEntity : PModifiableTypedEntity<SourceRootEntity>() {
  var module: ModuleEntity by MutableManyToOne.HardRef.NotNull(SourceRootEntity::class, ModuleEntity::class)
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var tests: Boolean by EntityDataDelegation()
  var rootType: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addSourceRootEntity(module: ModuleEntity,
                                                      url: VirtualFileUrl,
                                                      tests: Boolean,
                                                      rootType: String, source: EntitySource) = addEntity(
  ModifiableSourceRootEntity::class.java, source) {
  this.module = module
  this.url = url
  this.tests = tests
  this.rootType = rootType
}

class ModifiableJavaSourceRootEntity : PModifiableTypedEntity<JavaSourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.HardRef.NotNull(JavaSourceRootEntity::class, SourceRootEntity::class)
  var generated: Boolean by EntityDataDelegation()
  var packagePrefix: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                          generated: Boolean,
                                                          packagePrefix: String, source: EntitySource) = addEntity(
  ModifiableJavaSourceRootEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.packagePrefix = packagePrefix
}

class ModifiableJavaResourceRootEntity : PModifiableTypedEntity<JavaResourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.HardRef.NotNull(JavaResourceRootEntity::class, SourceRootEntity::class)
  var generated: Boolean by EntityDataDelegation()
  var relativeOutputPath: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                            generated: Boolean,
                                                            relativeOutputPath: String, source: EntitySource) = addEntity(
  ModifiableJavaResourceRootEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.relativeOutputPath = relativeOutputPath
}

class ModifiableCustomSourceRootPropertiesEntity : PModifiableTypedEntity<CustomSourceRootPropertiesEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.HardRef.NotNull(CustomSourceRootPropertiesEntity::class, SourceRootEntity::class)
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                                      propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomSourceRootPropertiesEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableContentRootEntity : PModifiableTypedEntity<ContentRootEntity>() {
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var excludedUrls: List<VirtualFileUrl> by VirtualFileUrlListProperty()
  var excludedPatterns: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableManyToOne.HardRef.NotNull(ContentRootEntity::class, ModuleEntity::class)
}

fun TypedEntityStorageDiffBuilder.addContentRootEntity(url: VirtualFileUrl,
                                                       excludedUrls: List<VirtualFileUrl>,
                                                       excludedPatterns: List<String>,
                                                       module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableContentRootEntity::class.java, source) {
  this.url = url
  this.excludedUrls = excludedUrls
  this.excludedPatterns = excludedPatterns
  this.module = module
}

class ModifiableLibraryEntity : PModifiableTypedEntity<LibraryEntity>() {
  var tableId: LibraryTableId by EntityDataDelegation()
  var name: String by EntityDataDelegation()
  var roots: List<LibraryRoot> by EntityDataDelegation()
  var excludedRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

fun TypedEntityStorageDiffBuilder.addLibraryEntity(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                   excludedRoots: List<VirtualFileUrl>, source: EntitySource) = addEntity(
  ModifiableLibraryEntity::class.java, source) {
  this.tableId = tableId
  this.name = name
  this.roots = roots
  this.excludedRoots = excludedRoots
}

class ModifiableLibraryPropertiesEntity : PModifiableTypedEntity<LibraryPropertiesEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.HardRef.NotNull(LibraryPropertiesEntity::class, LibraryEntity::class, true)
  var libraryType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addLibraryPropertiesEntity(library: LibraryEntity,
                                                             libraryType: String,
                                                             propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableLibraryPropertiesEntity::class.java, source) {
  this.library = library
  this.libraryType = libraryType
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableSdkEntity : PModifiableTypedEntity<SdkEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.HardRef.NotNull(SdkEntity::class, LibraryEntity::class, true)
  var homeUrl: VirtualFileUrl by VirtualFileUrlProperty()
}

fun TypedEntityStorageDiffBuilder.addSdkEntity(library: LibraryEntity,
                                               homeUrl: VirtualFileUrl, source: EntitySource) = addEntity(ModifiableSdkEntity::class.java,
                                                                                                          source) {
  this.library = library
  this.homeUrl = homeUrl
}

class ModifiableExternalSystemModuleOptionsEntity : PModifiableTypedEntity<ExternalSystemModuleOptionsEntity>() {
  var module: ModuleEntity by MutableOneToOneChild.HardRef.NotNull(ExternalSystemModuleOptionsEntity::class, ModuleEntity::class, true)
  var externalSystem: String? by EntityDataDelegation()
  var externalSystemModuleVersion: String? by EntityDataDelegation()

  var linkedProjectPath: String? by EntityDataDelegation()
  var linkedProjectId: String? by EntityDataDelegation()
  var rootProjectPath: String? by EntityDataDelegation()

  var externalSystemModuleGroup: String? by EntityDataDelegation()
  var externalSystemModuleType: String? by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.getOrCreateExternalSystemModuleOptions(module: ModuleEntity, source: EntitySource): ExternalSystemModuleOptionsEntity =
  module.externalSystemOptions ?: addEntity(ModifiableExternalSystemModuleOptionsEntity::class.java, source) {
    this.module = module
  }

class ModifiableFacetEntity : PModifiableTypedEntity<FacetEntity>() {
  var name: String by EntityDataDelegation()
  var facetType: String by EntityDataDelegation()
  var configurationXmlTag: String? by EntityDataDelegation()

  var module: ModuleEntity by MutableManyToOne.HardRef.NotNull(FacetEntity::class, ModuleEntity::class)
  var underlyingFacet: FacetEntity? by MutableOneToOneChild.HardRef.Nullable(FacetEntity::class, FacetEntity::class, true)
}

fun TypedEntityStorageDiffBuilder.addFacetEntity(name: String, facetType: String, configurationXmlTag: String?, module: ModuleEntity,
                                                 underlyingFacet: FacetEntity?, source: EntitySource) =
  addEntity(ModifiableFacetEntity::class.java, source) {
    this.name = name
    this.facetType = facetType
    this.configurationXmlTag = configurationXmlTag
    this.module = module
    this.underlyingFacet = underlyingFacet
  }

class ModifiableArtifactEntity : PModifiableTypedEntity<ArtifactEntity>() {
  var name: String by EntityDataDelegation()
  var artifactType: String by EntityDataDelegation()
  var includeInProjectBuild: Boolean by EntityDataDelegation()
  var outputUrl: VirtualFileUrl by VirtualFileUrlProperty()
  var rootElement: CompositePackagingElementEntity by MutableOneToAbstractOneChild.HardRef(ArtifactEntity::class,
                                                                                           CompositePackagingElementEntity::class)
}

fun TypedEntityStorageDiffBuilder.addArtifactEntity(name: String,
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

class ModifiableArtifactPropertiesEntity : PModifiableTypedEntity<ArtifactPropertiesEntity>() {
  var artifact: ArtifactEntity by MutableManyToOne.HardRef.NotNull(ArtifactPropertiesEntity::class, ArtifactEntity::class)
  var providerType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addArtifactPropertisEntity(artifact: ArtifactEntity,
                                                             providerType: String,
                                                             propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableArtifactPropertiesEntity::class.java, source) {
  this.artifact = artifact
  this.providerType = providerType
  this.propertiesXmlTag = propertiesXmlTag
}

class ModifiableArtifactRootElementEntity : PModifiableTypedEntity<ArtifactRootElementEntity>() {
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany.HardRef(ArtifactRootElementEntity::class,
                                                                                     PackagingElementEntity::class)
}

fun TypedEntityStorageDiffBuilder.addArtifactRootElementEntity(children: MutableList<PackagingElementEntity>,
                                                               source: EntitySource) = addEntity(
  ModifiableArtifactRootElementEntity::class.java, source) { this.children = children.asSequence() }

class ModifiableDirectoryPackagingElementEntity : PModifiableTypedEntity<DirectoryPackagingElementEntity>() {
  var directoryName: String by EntityDataDelegation()
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany.HardRef(DirectoryPackagingElementEntity::class,
                                                                                     PackagingElementEntity::class)
}

fun TypedEntityStorageDiffBuilder.addDirectoryPackagingElementEntity(directoryName: String,
                                                                     children: MutableList<PackagingElementEntity>,
                                                                     source: EntitySource) = addEntity(
  ModifiableDirectoryPackagingElementEntity::class.java,
  source) { this.directoryName = directoryName; this.children = children.asSequence() }

class ModifiableArchivePackagingElementEntity : PModifiableTypedEntity<ArchivePackagingElementEntity>() {
  var fileName: String by EntityDataDelegation()
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany.HardRef(ArchivePackagingElementEntity::class,
                                                                                     PackagingElementEntity::class)
}

fun TypedEntityStorageDiffBuilder.addArchivePackagingElementEntity(fileName: String,
                                                                   children: MutableList<PackagingElementEntity>,
                                                                   source: EntitySource) = addEntity(
  ModifiableArchivePackagingElementEntity::class.java, source) { this.fileName = fileName; this.children = children.asSequence() }

class ModifiableArtifactOutputPackagingElementEntity : PModifiableTypedEntity<ArtifactOutputPackagingElementEntity>() {
  var artifact: ArtifactId by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addArtifactOutputPackagingElementEntity(artifact: ArtifactId, source: EntitySource) = addEntity(
  ModifiableArtifactOutputPackagingElementEntity::class.java, source) { this.artifact = artifact }

class ModifiableModuleOutputPackagingElementEntity : PModifiableTypedEntity<ModuleOutputPackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addModuleOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleOutputPackagingElementEntity::class.java, source) { this.module = module }

class ModifiableLibraryFilesPackagingElementEntity : PModifiableTypedEntity<LibraryFilesPackagingElementEntity>() {
  var library: LibraryId by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addLibraryFilesPackagingElementEntity(library: LibraryId, source: EntitySource) = addEntity(
  ModifiableLibraryFilesPackagingElementEntity::class.java, source) { this.library = library }

class ModifiableModuleSourcePackagingElementEntity : PModifiableTypedEntity<ModuleSourcePackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addModuleSourcePackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleSourcePackagingElementEntity::class.java, source) { this.module = module }

class ModifiableModuleTestOutputPackagingElementEntity : PModifiableTypedEntity<ModuleTestOutputPackagingElementEntity>() {
  var module: ModuleId by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addModuleTestOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleTestOutputPackagingElementEntity::class.java, source) { this.module = module }

class ModifiableDirectoryCopyPackagingElementEntity : PModifiableTypedEntity<DirectoryCopyPackagingElementEntity>() {
  var directory: VirtualFileUrl by VirtualFileUrlProperty()
}

fun TypedEntityStorageDiffBuilder.addDirectoryCopyPackagingElementEntity(directory: VirtualFileUrl, source: EntitySource) = addEntity(
  ModifiableDirectoryCopyPackagingElementEntity::class.java, source) { this.directory = directory }

class ModifiableExtractedDirectoryPackagingElementEntity : PModifiableTypedEntity<ExtractedDirectoryPackagingElementEntity>() {
  var archive: VirtualFileUrl by VirtualFileUrlProperty()
  var pathInArchive: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addExtractedDirectoryPackagingElementEntity(archive: VirtualFileUrl,
                                                                              pathInArchive: String, source: EntitySource) = addEntity(
  ModifiableExtractedDirectoryPackagingElementEntity::class.java, source) {
  this.archive = archive
  this.pathInArchive = pathInArchive
}

class ModifiableFileCopyPackagingElementEntity : PModifiableTypedEntity<FileCopyPackagingElementEntity>() {
  var file: VirtualFileUrl by VirtualFileUrlProperty()
  var renamedOutputFileName: String? by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addFileCopyPackagingElementEntity(file: VirtualFileUrl,
                                                                    renamedOutputFileName: String?, source: EntitySource) = addEntity(
  ModifiableFileCopyPackagingElementEntity::class.java, source) {
  this.file = file
  this.renamedOutputFileName = renamedOutputFileName
}

class ModifiableCustomPackagingElementEntity : PModifiableTypedEntity<CustomPackagingElementEntity>() {
  var typeId: String by EntityDataDelegation()
  var propertiesXmlTag: String by EntityDataDelegation()
}

fun TypedEntityStorageDiffBuilder.addCustomPackagingElementEntity(typeId: String,
                                                                  propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomPackagingElementEntity::class.java, source) {
  this.typeId = typeId
  this.propertiesXmlTag = propertiesXmlTag
}
