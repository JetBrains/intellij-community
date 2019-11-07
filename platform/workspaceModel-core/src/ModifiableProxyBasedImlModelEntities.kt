package com.intellij.workspace.api

interface ModifiableModuleEntity : ModuleEntity, ModifiableTypedEntity<ModuleEntity> {
  override var name: String
  override var dependencies: List<ModuleDependencyItem>
}

fun TypedEntityStorageDiffBuilder.addModuleEntity(name: String, dependencies: List<ModuleDependencyItem>, source: EntitySource) = addEntity(
  ModifiableModuleEntity::class.java, source) {
  this.name = name
  this.dependencies = dependencies
}

interface ModifiableJavaModuleSettingsEntity : JavaModuleSettingsEntity, ModifiableTypedEntity<JavaModuleSettingsEntity> {
  override var inheritedCompilerOutput: Boolean
  override var excludeOutput: Boolean
  override var compilerOutput: VirtualFileUrl?
  override var compilerOutputForTests: VirtualFileUrl?

  override var module: ModuleEntity
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

interface ModifiableModuleCustomImlDataEntity : ModuleCustomImlDataEntity, ModifiableTypedEntity<ModuleCustomImlDataEntity> {
  override var rootManagerTagCustomData: String
  override var module: ModuleEntity
}

fun TypedEntityStorageDiffBuilder.addModuleCustomImlDataEntity(rootManagerTagCustomData: String,
                                                               module: ModuleEntity,
                                                               source: EntitySource) = addEntity(
  ModifiableModuleCustomImlDataEntity::class.java, source) {
  this.rootManagerTagCustomData = rootManagerTagCustomData
  this.module = module
}

interface ModifiableModuleGroupPathEntity : ModuleGroupPathEntity, ModifiableTypedEntity<ModuleGroupPathEntity> {
  override var path: List<String>
  override var module: ModuleEntity
}

fun TypedEntityStorageDiffBuilder.addModuleGroupPathEntity(path: List<String>,
                                                           module: ModuleEntity, source: EntitySource) = addEntity(
  ModifiableModuleGroupPathEntity::class.java, source) {
  this.path = path
  this.module = module
}

interface ModifiableSourceRootEntity : SourceRootEntity, ModifiableTypedEntity<SourceRootEntity> {
  override var module: ModuleEntity
  override var url: VirtualFileUrl
  override var tests: Boolean
  override var rootType: String
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

interface ModifiableJavaSourceRootEntity : JavaSourceRootEntity, ModifiableTypedEntity<JavaSourceRootEntity> {
  override var sourceRoot: SourceRootEntity
  override var generated: Boolean
  override var packagePrefix: String
}

fun TypedEntityStorageDiffBuilder.addJavaSourceRootEntity(sourceRoot: SourceRootEntity,
                                                          generated: Boolean,
                                                          packagePrefix: String, source: EntitySource) = addEntity(
  ModifiableJavaSourceRootEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.packagePrefix = packagePrefix
}

interface ModifiableJavaResourceRootEntity : JavaResourceRootEntity, ModifiableTypedEntity<JavaResourceRootEntity> {
  override var sourceRoot: SourceRootEntity
  override var generated: Boolean
  override var relativeOutputPath: String
}

fun TypedEntityStorageDiffBuilder.addJavaResourceRootEntity(sourceRoot: SourceRootEntity,
                                                            generated: Boolean,
                                                            relativeOutputPath: String, source: EntitySource) = addEntity(
  ModifiableJavaResourceRootEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.generated = generated
  this.relativeOutputPath = relativeOutputPath
}

interface ModifiableCustomSourceRootPropertiesEntity : CustomSourceRootPropertiesEntity, ModifiableTypedEntity<CustomSourceRootPropertiesEntity> {
  override var sourceRoot: SourceRootEntity
  override var propertiesXmlTag: String
}

fun TypedEntityStorageDiffBuilder.addCustomSourceRootPropertiesEntity(sourceRoot: SourceRootEntity,
                                                                      propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomSourceRootPropertiesEntity::class.java, source) {
  this.sourceRoot = sourceRoot
  this.propertiesXmlTag = propertiesXmlTag
}

interface ModifiableContentRootEntity : ContentRootEntity, ModifiableTypedEntity<ContentRootEntity> {
  override var url: VirtualFileUrl
  override var excludedUrls: List<VirtualFileUrl>
  override var excludedPatterns: List<String>
  override var module: ModuleEntity
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

interface ModifiableLibraryEntity : LibraryEntity, ModifiableTypedEntity<LibraryEntity> {
  override var tableId: LibraryTableId
  override var name: String
  override var roots: List<LibraryRoot>
  override var excludedRoots: List<VirtualFileUrl>
}

fun TypedEntityStorageDiffBuilder.addLibraryEntity(name: String, tableId: LibraryTableId, roots: List<LibraryRoot>,
                                                   excludedRoots: List<VirtualFileUrl>, source: EntitySource) = addEntity(
  ModifiableLibraryEntity::class.java, source) {
  this.tableId = tableId
  this.name = name
  this.roots = roots
  this.excludedRoots = excludedRoots
}

interface ModifiableLibraryPropertiesEntity : LibraryPropertiesEntity, ModifiableTypedEntity<LibraryPropertiesEntity> {
  override var library: LibraryEntity
  override var libraryType: String
  override var propertiesXmlTag: String?
}

fun TypedEntityStorageDiffBuilder.addLibraryPropertiesEntity(library: LibraryEntity,
                                                             libraryType: String,
                                                             propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableLibraryPropertiesEntity::class.java, source) {
  this.library = library
  this.libraryType = libraryType
  this.propertiesXmlTag = propertiesXmlTag
}

interface ModifiableSdkEntity : SdkEntity, ModifiableTypedEntity<SdkEntity> {
  override var library: LibraryEntity
  override var homeUrl: VirtualFileUrl
}

fun TypedEntityStorageDiffBuilder.addSdkEntity(library: LibraryEntity,
                                               homeUrl: VirtualFileUrl, source: EntitySource) = addEntity(ModifiableSdkEntity::class.java,
                                                                                                          source) {
  this.library = library
  this.homeUrl = homeUrl
}

interface ModifiableArtifactEntity : ArtifactEntity, ModifiableTypedEntity<ArtifactEntity> {
  override var name: String
  override var artifactType: String
  override var includeInProjectBuild: Boolean
  override var outputUrl: VirtualFileUrl
  override var rootElement: CompositePackagingElementEntity
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

interface ModifiableArtifactPropertiesEntity : ArtifactPropertiesEntity, ModifiableTypedEntity<ArtifactPropertiesEntity> {
  override var artifact: ArtifactEntity
  override var providerType: String
  override var propertiesXmlTag: String?
}

fun TypedEntityStorageDiffBuilder.addArtifactPropertisEntity(artifact: ArtifactEntity,
                                                             providerType: String,
                                                             propertiesXmlTag: String?, source: EntitySource) = addEntity(
  ModifiableArtifactPropertiesEntity::class.java, source) {
  this.artifact = artifact
  this.providerType = providerType
  this.propertiesXmlTag = propertiesXmlTag
}

interface ModifiableArtifactRootElementEntity : ArtifactRootElementEntity, ModifiableTypedEntity<ArtifactRootElementEntity> {
  override var children: MutableList<PackagingElementEntity>
}

fun TypedEntityStorageDiffBuilder.addArtifactRootElementEntity(children: MutableList<PackagingElementEntity>,
                                                               source: EntitySource) = addEntity(
  ModifiableArtifactRootElementEntity::class.java, source) { this.children = children }

interface ModifiableDirectoryPackagingElementEntity : DirectoryPackagingElementEntity, ModifiableTypedEntity<DirectoryPackagingElementEntity> {
  override var directoryName: String
  override var children: MutableList<PackagingElementEntity>
}

fun TypedEntityStorageDiffBuilder.addDirectoryPackagingElementEntity(directoryName: String,
                                                                     children: MutableList<PackagingElementEntity>,
                                                                     source: EntitySource) = addEntity(
  ModifiableDirectoryPackagingElementEntity::class.java, source) { this.directoryName = directoryName; this.children = children }

interface ModifiableArchivePackagingElementEntity : ArchivePackagingElementEntity, ModifiableTypedEntity<ArchivePackagingElementEntity> {
  override var fileName: String
  override var children: MutableList<PackagingElementEntity>
}

fun TypedEntityStorageDiffBuilder.addArchivePackagingElementEntity(fileName: String,
                                                                   children: MutableList<PackagingElementEntity>,
                                                                   source: EntitySource) = addEntity(
  ModifiableArchivePackagingElementEntity::class.java, source) { this.fileName = fileName; this.children = children }

interface ModifiableArtifactOutputPackagingElementEntity : ArtifactOutputPackagingElementEntity, ModifiableTypedEntity<ArtifactOutputPackagingElementEntity> {
  override var artifact: ArtifactId
}

fun TypedEntityStorageDiffBuilder.addArtifactOutputPackagingElementEntity(artifact: ArtifactId, source: EntitySource) = addEntity(
  ModifiableArtifactOutputPackagingElementEntity::class.java, source) { this.artifact = artifact }

interface ModifiableModuleOutputPackagingElementEntity : ModuleOutputPackagingElementEntity, ModifiableTypedEntity<ModuleOutputPackagingElementEntity> {
  override var module: ModuleId
}

fun TypedEntityStorageDiffBuilder.addModuleOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleOutputPackagingElementEntity::class.java, source) { this.module = module }

interface ModifiableLibraryFilesPackagingElementEntity : LibraryFilesPackagingElementEntity, ModifiableTypedEntity<LibraryFilesPackagingElementEntity> {
  override var library: LibraryId
}

fun TypedEntityStorageDiffBuilder.addLibraryFilesPackagingElementEntity(library: LibraryId, source: EntitySource) = addEntity(
  ModifiableLibraryFilesPackagingElementEntity::class.java, source) { this.library = library }

interface ModifiableModuleSourcePackagingElementEntity : ModuleSourcePackagingElementEntity, ModifiableTypedEntity<ModuleSourcePackagingElementEntity> {
  override var module: ModuleId
}

fun TypedEntityStorageDiffBuilder.addModuleSourcePackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleSourcePackagingElementEntity::class.java, source) { this.module = module }

interface ModifiableModuleTestOutputPackagingElementEntity : ModuleTestOutputPackagingElementEntity, ModifiableTypedEntity<ModuleTestOutputPackagingElementEntity> {
  override var module: ModuleId
}

fun TypedEntityStorageDiffBuilder.addModuleTestOutputPackagingElementEntity(module: ModuleId, source: EntitySource) = addEntity(
  ModifiableModuleTestOutputPackagingElementEntity::class.java, source) { this.module = module }

interface ModifiableDirectoryCopyPackagingElementEntity : DirectoryCopyPackagingElementEntity, ModifiableTypedEntity<DirectoryCopyPackagingElementEntity> {
  override var directory: VirtualFileUrl
}

fun TypedEntityStorageDiffBuilder.addDirectoryCopyPackagingElementEntity(directory: VirtualFileUrl, source: EntitySource) = addEntity(
  ModifiableDirectoryCopyPackagingElementEntity::class.java, source) { this.directory = directory }

interface ModifiableExtractedDirectoryPackagingElementEntity : ExtractedDirectoryPackagingElementEntity, ModifiableTypedEntity<ExtractedDirectoryPackagingElementEntity> {
  override var archive: VirtualFileUrl
  override var pathInArchive: String
}

fun TypedEntityStorageDiffBuilder.addExtractedDirectoryPackagingElementEntity(archive: VirtualFileUrl,
                                                                              pathInArchive: String, source: EntitySource) = addEntity(
  ModifiableExtractedDirectoryPackagingElementEntity::class.java, source) {
  this.archive = archive
  this.pathInArchive = pathInArchive
}

interface ModifiableFileCopyPackagingElementEntity : FileCopyPackagingElementEntity, ModifiableTypedEntity<FileCopyPackagingElementEntity> {
  override var file: VirtualFileUrl
  override var renamedOutputFileName: String?
}

fun TypedEntityStorageDiffBuilder.addFileCopyPackagingElementEntity(file: VirtualFileUrl,
                                                                    renamedOutputFileName: String?, source: EntitySource) = addEntity(
  ModifiableFileCopyPackagingElementEntity::class.java, source) {
  this.file = file
  this.renamedOutputFileName = renamedOutputFileName
}

interface ModifiableCustomPackagingElementEntity : CustomPackagingElementEntity, ModifiableTypedEntity<CustomPackagingElementEntity> {
  override var typeId: String
  override var propertiesXmlTag: String
}

fun TypedEntityStorageDiffBuilder.addCustomPackagingElementEntity(typeId: String,
                                                                  propertiesXmlTag: String, source: EntitySource) = addEntity(
  ModifiableCustomPackagingElementEntity::class.java, source) {
  this.typeId = typeId
  this.propertiesXmlTag = propertiesXmlTag
}
