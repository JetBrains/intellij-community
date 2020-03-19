package com.intellij.workspace.api

/**
 * A prototype of implementation of the current (legacy) project model via [TypedEntity]. It uses similar concepts to simplify implementation
 * of the bridge to the current model. Some peculiarities are fixed though:
 * * source roots are moved of from content entries, content roots are stored separately in a module;
 * * module libraries are stored in a real library table, unnamed libraries aren't allowed;
 * * 'jar directory' flag is stored in a library root itself;
 * * SDKs are represented as [LibraryEntity] with additional properties specified in [SdkEntity] attached to it.
 */

interface ModuleEntity : TypedEntityWithPersistentId, ReferableTypedEntity {
  val name: String
  val dependencies: List<ModuleDependencyItem>

  @JvmDefault
  override fun persistentId(): ModuleId = ModuleId(name)

  @JvmDefault val sourceRoots: Sequence<SourceRootEntity>
    get() = referrers(SourceRootEntity::module)
  @JvmDefault val contentRoots: Sequence<ContentRootEntity>
    get() = referrers(ContentRootEntity::module)
  @JvmDefault val customImlData: ModuleCustomImlDataEntity?
    get() = referrers(ModuleCustomImlDataEntity::module).firstOrNull()

  @JvmDefault val groupPath: ModuleGroupPathEntity?
    get() = referrers(ModuleGroupPathEntity::module).firstOrNull()

  @JvmDefault val javaSettings: JavaModuleSettingsEntity?
    get() = referrers(JavaModuleSettingsEntity::module).firstOrNull()
}

interface JavaModuleSettingsEntity : TypedEntity {
  val inheritedCompilerOutput: Boolean
  val excludeOutput: Boolean
  val compilerOutput: VirtualFileUrl?
  val compilerOutputForTests: VirtualFileUrl?

  val module: ModuleEntity
}

interface ModuleCustomImlDataEntity : TypedEntity {
  val rootManagerTagCustomData: String
  val module: ModuleEntity
}

interface ModuleGroupPathEntity : TypedEntity {
  val path: List<String>
  val module: ModuleEntity
}

data class ModuleId(val name: String) : PersistentEntityId<ModuleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

sealed class ModuleDependencyItem {
  sealed class Exportable : ModuleDependencyItem() {
    abstract val exported: Boolean
    abstract val scope: DependencyScope

    abstract fun withScope(scope: DependencyScope): Exportable
    abstract fun withExported(exported: Boolean): Exportable

    data class ModuleDependency(
      val module: ModuleId,
      override val exported: Boolean,
      override val scope: DependencyScope,
      val productionOnTest: Boolean
    ) : Exportable() {
      override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
      override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
    }

    data class LibraryDependency(
      val library: LibraryId,
      override val exported: Boolean,
      override val scope: DependencyScope
    ) : Exportable() {
      override fun withScope(scope: DependencyScope): Exportable = copy(scope = scope)
      override fun withExported(exported: Boolean): Exportable = copy(exported = exported)
    }
  }

  //todo use LibraryProxyId to refer to SDK instead
  data class SdkDependency(val sdkName: String, val sdkType: String) : ModuleDependencyItem()

  object InheritedSdkDependency : ModuleDependencyItem()
  object ModuleSourceDependency : ModuleDependencyItem()
  enum class DependencyScope { COMPILE, TEST, RUNTIME, PROVIDED }
}

interface SourceRootEntity : TypedEntity, ReferableTypedEntity {
  val module: ModuleEntity
  val url: VirtualFileUrl
  val tests: Boolean
  val rootType: String
}

interface JavaSourceRootEntity : TypedEntity {
  val sourceRoot: SourceRootEntity
  val generated: Boolean
  val packagePrefix: String
}

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootEntity? = referrers(JavaSourceRootEntity::sourceRoot).firstOrNull()

interface JavaResourceRootEntity : TypedEntity {
  val sourceRoot: SourceRootEntity
  val generated: Boolean
  val relativeOutputPath: String
}

fun SourceRootEntity.asJavaResourceRoot() = referrers(JavaResourceRootEntity::sourceRoot).firstOrNull()

interface CustomSourceRootPropertiesEntity : TypedEntity {
  val sourceRoot: SourceRootEntity
  val propertiesXmlTag: String
}

fun SourceRootEntity.asCustomSourceRoot() = referrers(CustomSourceRootPropertiesEntity::sourceRoot).firstOrNull()

interface ContentRootEntity : TypedEntity, ReferableTypedEntity {
  val url: VirtualFileUrl
  val excludedUrls: List<VirtualFileUrl>
  val excludedPatterns: List<String>
  val module: ModuleEntity
}

/**
 * This entity stores order of artifacts in file. This is needed to ensure that source roots are saved in the same order to avoid
 * unnecessary modifications of file.
 */
interface SourceRootOrderEntity : ModifiableTypedEntity<SourceRootOrderEntity> {
  var orderOfSourceRoots: List<VirtualFileUrl>

  var contentRootEntity: ContentRootEntity
}

fun ContentRootEntity.getSourceRootOrder() = referrers(SourceRootOrderEntity::contentRootEntity).firstOrNull()

fun ModuleEntity.getModuleLibraries(storage: TypedEntityStorage): Sequence<LibraryEntity> {
  return storage.entities(LibraryEntity::class.java).filter { (it.persistentId().tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val TypedEntityStorage.projectLibraries
  get() = entities(LibraryEntity::class.java).filter { it.persistentId().tableId == LibraryTableId.ProjectLibraryTableId }

sealed class LibraryTableId {
  data class ModuleLibraryTableId(val moduleId: ModuleId) : LibraryTableId() {
    override val level: String
      get() = "module"
  }
  object ProjectLibraryTableId : LibraryTableId() {
    override val level: String
      get() = "project"
  }
  data class GlobalLibraryTableId(override val level: String) : LibraryTableId()
  abstract val level: String
}

interface LibraryEntity : TypedEntityWithPersistentId, ReferableTypedEntity {
  val tableId: LibraryTableId
  val name: String
  val roots: List<LibraryRoot>
  val excludedRoots: List<VirtualFileUrl>

  @JvmDefault
  override fun persistentId(): LibraryId = LibraryId(name, tableId)
}

data class LibraryId(val name: String, val tableId: LibraryTableId) : PersistentEntityId<LibraryEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

data class LibraryRootTypeId(val name: String)

data class LibraryRoot(
  val url: VirtualFileUrl,
  val type: LibraryRootTypeId,
  val inclusionOptions: InclusionOptions
) {

  enum class InclusionOptions {
    ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY
  }
}

interface LibraryPropertiesEntity : TypedEntity {
  val library: LibraryEntity
  val libraryType: String
  val propertiesXmlTag: String?
}

fun LibraryEntity.getCustomProperties() = referrers(LibraryPropertiesEntity::library).firstOrNull()

interface SdkEntity : TypedEntity {
  val library: LibraryEntity
  val homeUrl: VirtualFileUrl
}

interface FacetEntity : TypedEntityWithPersistentId, ReferableTypedEntity {
  val name: String
  val facetType: String
  val configurationXmlTag: String?
  val module: ModuleEntity
  val underlyingFacet: FacetEntity?

  @JvmDefault
  override fun persistentId(): FacetId = FacetId(name, facetType, module.persistentId())
}

data class FacetId(val name: String, val type: String, override val parentId: ModuleId) : PersistentEntityId<FacetEntity>() {
  override val presentableName: String
    get() = name
}

val FacetEntity.subFacets: Sequence<FacetEntity>
  get() = referrers(FacetEntity::underlyingFacet)

val ModuleEntity.facets: Sequence<FacetEntity>
  get() = referrers(FacetEntity::module)


data class ArtifactId(val name: String) : PersistentEntityId<ArtifactEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

interface ArtifactEntity : TypedEntityWithPersistentId, ReferableTypedEntity {
  val name: String
  val artifactType: String
  val includeInProjectBuild: Boolean
  val outputUrl: VirtualFileUrl
  val rootElement: CompositePackagingElementEntity
  @JvmDefault
  override fun persistentId(): ArtifactId = ArtifactId(name)

  @JvmDefault
  val customProperties: Sequence<ArtifactPropertiesEntity>
    get() = referrers(ArtifactPropertiesEntity::artifact)

}

interface ArtifactPropertiesEntity : TypedEntity {
  val artifact: ArtifactEntity
  val providerType: String
  val propertiesXmlTag: String?
}

interface PackagingElementEntity : TypedEntity

interface CompositePackagingElementEntity : PackagingElementEntity {
  val children: List<PackagingElementEntity>
}

interface DirectoryPackagingElementEntity : CompositePackagingElementEntity {
  val directoryName: String
}

interface ArchivePackagingElementEntity : CompositePackagingElementEntity {
  val fileName: String
}

interface ArtifactRootElementEntity : CompositePackagingElementEntity

interface ArtifactOutputPackagingElementEntity : PackagingElementEntity {
  val artifact: ArtifactId
}

interface ModuleOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId
}

interface LibraryFilesPackagingElementEntity : PackagingElementEntity {
  val library: LibraryId
}

interface ModuleSourcePackagingElementEntity : PackagingElementEntity {
  val module: ModuleId
}

interface ModuleTestOutputPackagingElementEntity : PackagingElementEntity {
  val module: ModuleId
}

interface DirectoryCopyPackagingElementEntity : PackagingElementEntity {
  val directory: VirtualFileUrl
}

interface ExtractedDirectoryPackagingElementEntity : PackagingElementEntity {
  val archive: VirtualFileUrl
  val pathInArchive: String
}

interface FileCopyPackagingElementEntity : PackagingElementEntity {
  val file: VirtualFileUrl
  val renamedOutputFileName: String?
}

interface CustomPackagingElementEntity : PackagingElementEntity {
  val typeId: String
  val propertiesXmlTag: String
}