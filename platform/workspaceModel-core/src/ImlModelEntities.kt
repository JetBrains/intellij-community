package com.intellij.workspace.api

import com.intellij.workspace.api.pstorage.*
import com.intellij.workspace.api.pstorage.indices.VirtualFileUrlListProperty
import com.intellij.workspace.api.pstorage.references.*
import java.io.Serializable

/**
 * A prototype of implementation of the current (legacy) project model via [TypedEntity]. It uses similar concepts to simplify implementation
 * of the bridge to the current model. Some peculiarities are fixed though:
 * * source roots are moved of from content entries, content roots are stored separately in a module;
 * * module libraries are stored in a real library table, unnamed libraries aren't allowed;
 * * 'jar directory' flag is stored in a library root itself;
 * * SDKs are represented as [LibraryEntity] with additional properties specified in [SdkEntity] attached to it.
 */

@Suppress("unused")
class ModuleEntityData : PEntityData<ModuleEntity>(), PSoftLinkable {
  lateinit var name: String
  var type: String? = null
  lateinit var dependencies: List<ModuleDependencyItem>

  @ExperimentalStdlibApi
  override fun getLinks(): List<PersistentEntityId<*>> {
    return buildList {
      dependencies.forEach { dependency ->
        when (dependency) {
          is ModuleDependencyItem.Exportable.ModuleDependency -> this.add(dependency.module)
          is ModuleDependencyItem.Exportable.LibraryDependency -> this.add(dependency.library)
          else -> Unit
        }
      }
    }
  }

  override fun updateLink(beforeLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    var changed = false
    val res = dependencies.map { dependency ->
      when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> {
          if (dependency.module == beforeLink) {
            changed = true
            dependency.copy(module = newLink as ModuleId)
          }
          else dependency
        }
        is ModuleDependencyItem.Exportable.LibraryDependency -> {
          if (dependency.library == beforeLink) {
            changed = true
            dependency.copy(library = newLink as LibraryId)
          }
          else dependency
        }
        else -> dependency
      }
    }
    if (changed) {
      this.dependencies = res
    }
    return changed
  }

  override fun createEntity(snapshot: TypedEntityStorage): ModuleEntity = ModuleEntity(name, type, dependencies.toList()).also {
    addMetaData(it, snapshot)
  }
}

class ModuleEntity(
  val name: String,
  val type: String?,
  val dependencies: List<ModuleDependencyItem>
) : TypedEntityWithPersistentId, PTypedEntity() {

  override fun persistentId(): ModuleId = ModuleId(name)

  val sourceRoots: Sequence<SourceRootEntity> by OneToMany.HardRef(SourceRootEntity::class, false)

  val contentRoots: Sequence<ContentRootEntity> by OneToMany.HardRef(ContentRootEntity::class, false)

  val customImlData: ModuleCustomImlDataEntity? by OneToOneParent.HardRef.Nullable(ModuleCustomImlDataEntity::class, false)

  val groupPath: ModuleGroupPathEntity? by OneToOneParent.HardRef.Nullable(ModuleGroupPathEntity::class, false)

  val javaSettings: JavaModuleSettingsEntity? by OneToOneParent.HardRef.Nullable(JavaModuleSettingsEntity::class, false)
}

@Suppress("unused")
class JavaModuleSettingsEntityData : PEntityData<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean = false
  var excludeOutput: Boolean = false
  var compilerOutput: VirtualFileUrl? = null
  var compilerOutputForTests: VirtualFileUrl? = null

  override fun createEntity(snapshot: TypedEntityStorage): JavaModuleSettingsEntity {
    return JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, compilerOutput, compilerOutputForTests).also { addMetaData(it, snapshot) }
  }
}

class JavaModuleSettingsEntity(
  val inheritedCompilerOutput: Boolean,
  val excludeOutput: Boolean,
  val compilerOutput: VirtualFileUrl?,
  val compilerOutputForTests: VirtualFileUrl?
) : PTypedEntity() {
  val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)
}

@Suppress("unused")
class ModuleCustomImlDataEntityData : PEntityData<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? = null
  lateinit var customModuleOptions: Map<String, String>

  override fun createEntity(snapshot: TypedEntityStorage): ModuleCustomImlDataEntity {
    return ModuleCustomImlDataEntity(rootManagerTagCustomData, customModuleOptions.toMap()).also { addMetaData(it, snapshot) }
  }
}

class ModuleCustomImlDataEntity(
  val rootManagerTagCustomData: String?,
  val customModuleOptions: Map<String, String>
) : PTypedEntity() {
  val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)
}

@Suppress("unused")
class ModuleGroupPathEntityData : PEntityData<ModuleGroupPathEntity>() {
  lateinit var path: List<String>

  override fun createEntity(snapshot: TypedEntityStorage): ModuleGroupPathEntity = ModuleGroupPathEntity(path.toList()).also { addMetaData(it, snapshot) }
}

class ModuleGroupPathEntity(
  val path: List<String>
) : PTypedEntity() {
  val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)
}

data class ModuleId(val name: String) : PersistentEntityId<ModuleEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

sealed class ModuleDependencyItem : Serializable {
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

@Suppress("unused")
class SourceRootEntityData : PEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  var tests: Boolean = false
  lateinit var rootType: String

  override fun createEntity(snapshot: TypedEntityStorage): SourceRootEntity = SourceRootEntity(url, tests, rootType).also { addMetaData(it, snapshot) }
}

open class SourceRootEntity(
  val url: VirtualFileUrl,
  val tests: Boolean,
  val rootType: String
) : PTypedEntity() {
  val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)
}

@Suppress("unused")
class JavaSourceRootEntityData : PEntityData<JavaSourceRootEntity>() {
  var generated: Boolean = false
  lateinit var packagePrefix: String

  override fun createEntity(snapshot: TypedEntityStorage): JavaSourceRootEntity = JavaSourceRootEntity(generated, packagePrefix).also { addMetaData(it, snapshot) }
}

class JavaSourceRootEntity(
  val generated: Boolean,
  val packagePrefix: String
) : PTypedEntity() {
  val sourceRoot: SourceRootEntity by ManyToOne.HardRef.NotNull(SourceRootEntity::class)
}

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootEntity? = referrers(JavaSourceRootEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class JavaResourceRootEntityData : PEntityData<JavaResourceRootEntity>() {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String

  override fun createEntity(snapshot: TypedEntityStorage): JavaResourceRootEntity = JavaResourceRootEntity(generated, relativeOutputPath).also { addMetaData(it, snapshot) }
}

class JavaResourceRootEntity(
  val generated: Boolean,
  val relativeOutputPath: String
) : PTypedEntity() {
  val sourceRoot: SourceRootEntity by ManyToOne.HardRef.NotNull(SourceRootEntity::class)
}

fun SourceRootEntity.asJavaResourceRoot() = referrers(JavaResourceRootEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class CustomSourceRootPropertiesEntityData : PEntityData<CustomSourceRootPropertiesEntity>() {
  lateinit var propertiesXmlTag: String
  override fun createEntity(snapshot: TypedEntityStorage): CustomSourceRootPropertiesEntity {
    return CustomSourceRootPropertiesEntity(propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class CustomSourceRootPropertiesEntity(
  val propertiesXmlTag: String
) : PTypedEntity() {
  val sourceRoot: SourceRootEntity by ManyToOne.HardRef.NotNull(SourceRootEntity::class)
}

fun SourceRootEntity.asCustomSourceRoot() = referrers(CustomSourceRootPropertiesEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class ContentRootEntityData : PEntityData<ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedUrls: List<VirtualFileUrl>
  lateinit var excludedPatterns: List<String>

  override fun createEntity(snapshot: TypedEntityStorage): ContentRootEntity {
    return ContentRootEntity(url, excludedUrls.toList(), excludedPatterns.toList()).also { addMetaData(it, snapshot) }
  }
}

open class ContentRootEntity(
  val url: VirtualFileUrl,
  val excludedUrls: List<VirtualFileUrl>,
  val excludedPatterns: List<String>
) : PTypedEntity() {
  open val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)
}

class FakeContentRootEntity(url: VirtualFileUrl, moduleEntity: ModuleEntity) : ContentRootEntity(url, emptyList(), emptyList()) {
  override val module: ModuleEntity = moduleEntity
  override var entitySource: EntitySource = moduleEntity.entitySource
  override fun hasEqualProperties(e: TypedEntity): Boolean = throw UnsupportedOperationException()
  override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> = throw UnsupportedOperationException()
}

/**
 * This entity stores order of artifacts in file. This is needed to ensure that source roots are saved in the same order to avoid
 * unnecessary modifications of file.
 */
@Suppress("unused")
class SourceRootOrderEntityData : PEntityData<SourceRootOrderEntity>() {
  lateinit var orderOfSourceRoots: List<VirtualFileUrl>
  override fun createEntity(snapshot: TypedEntityStorage): SourceRootOrderEntity {
    return SourceRootOrderEntity(orderOfSourceRoots.toList()).also { addMetaData(it, snapshot) }
  }
}

class SourceRootOrderEntity(
  var orderOfSourceRoots: List<VirtualFileUrl>
) : PTypedEntity() {
  val contentRootEntity: ContentRootEntity by OneToOneChild.HardRef.NotNull(ContentRootEntity::class, true)
}

class ModifiableSourceRootOrderEntity : PModifiableTypedEntity<SourceRootOrderEntity>() {
  var orderOfSourceRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()

  var contentRootEntity: ContentRootEntity by MutableOneToOneChild.HardRef.NotNull(SourceRootOrderEntity::class, ContentRootEntity::class,
                                                                                   true)
}

fun ContentRootEntity.getSourceRootOrder() = referrers(SourceRootOrderEntity::contentRootEntity).firstOrNull()

fun ModuleEntity.getModuleLibraries(storage: TypedEntityStorage): Sequence<LibraryEntity> {
  return storage.entities(
    LibraryEntity::class.java).filter { (it.persistentId().tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val TypedEntityStorage.projectLibraries
  get() = entities(LibraryEntity::class.java).filter { it.persistentId().tableId == LibraryTableId.ProjectLibraryTableId }

sealed class LibraryTableId : Serializable {
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

@Suppress("unused")
class LibraryEntityData : PEntityData<LibraryEntity>(), PSoftLinkable {
  lateinit var tableId: LibraryTableId
  lateinit var name: String
  lateinit var roots: List<LibraryRoot>
  lateinit var excludedRoots: List<VirtualFileUrl>

  override fun getLinks(): List<PersistentEntityId<*>> {
    val id = tableId
    return if (id is LibraryTableId.ModuleLibraryTableId) listOf(id.moduleId) else emptyList()
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    val id = tableId
    return if (id is LibraryTableId.ModuleLibraryTableId && id.moduleId == oldLink) {
      this.tableId = id.copy(moduleId = newLink as ModuleId)
      true
    }
    else false
  }

  override fun createEntity(snapshot: TypedEntityStorage): LibraryEntity {
    return LibraryEntity(tableId, name, roots.toList(), excludedRoots.toList()).also { addMetaData(it, snapshot) }
  }
}

open class LibraryEntity(
  open val tableId: LibraryTableId,
  val name: String,
  val roots: List<LibraryRoot>,
  val excludedRoots: List<VirtualFileUrl>
) : TypedEntityWithPersistentId, PTypedEntity() {
  override fun persistentId(): LibraryId = LibraryId(name, tableId)
}

class FakeLibraryEntity(name: String) : LibraryEntity(LibraryTableId.ProjectLibraryTableId, name, emptyList(), emptyList()) {
  override var entitySource: EntitySource
    get() = throw NotImplementedError()
    set(value) {
      throw NotImplementedError()
    }

  override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> = emptySequence()
  override val tableId: LibraryTableId
    get() = throw NotImplementedError()

  override fun hasEqualProperties(e: TypedEntity): Boolean {
    return e is LibraryEntity && e.name == name && e.roots.isEmpty() && e.excludedRoots.isEmpty()
  }
}

data class LibraryId(val name: String, val tableId: LibraryTableId) : PersistentEntityId<LibraryEntity>() {
  override val parentId: PersistentEntityId<*>?
    get() = null
  override val presentableName: String
    get() = name
}

data class LibraryRootTypeId(val name: String) : Serializable

data class LibraryRoot(
  val url: VirtualFileUrl,
  val type: LibraryRootTypeId,
  val inclusionOptions: InclusionOptions
) : Serializable {

  enum class InclusionOptions {
    ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY
  }
}

@Suppress("unused")
class LibraryPropertiesEntityData : PEntityData<LibraryPropertiesEntity>() {
  lateinit var libraryType: String
  var propertiesXmlTag: String? = null

  override fun createEntity(snapshot: TypedEntityStorage): LibraryPropertiesEntity {
    return LibraryPropertiesEntity(libraryType, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class LibraryPropertiesEntity(
  val libraryType: String,
  val propertiesXmlTag: String?
) : PTypedEntity() {
  val library: LibraryEntity by OneToOneChild.HardRef.NotNull(LibraryEntity::class, true)
}

fun LibraryEntity.getCustomProperties() = referrers(LibraryPropertiesEntity::library).firstOrNull()

@Suppress("unused")
class SdkEntityData : PEntityData<SdkEntity>() {
  lateinit var homeUrl: VirtualFileUrl

  override fun createEntity(snapshot: TypedEntityStorage): SdkEntity = SdkEntity(homeUrl).also { addMetaData(it, snapshot) }
}

class SdkEntity(
  val homeUrl: VirtualFileUrl
) : PTypedEntity() {
  val library: LibraryEntity by OneToOneChild.HardRef.NotNull(LibraryEntity::class, true)
}

@Suppress("unused")
class ExternalSystemModuleOptionsEntityData : PEntityData<ExternalSystemModuleOptionsEntity>() {
  var externalSystem: String? = null
  var externalSystemModuleVersion: String? = null

  var linkedProjectPath: String? = null
  var linkedProjectId: String? = null
  var rootProjectPath: String? = null

  var externalSystemModuleGroup: String? = null
  var externalSystemModuleType: String? = null

  override fun createEntity(snapshot: TypedEntityStorage): ExternalSystemModuleOptionsEntity {
    return ExternalSystemModuleOptionsEntity(externalSystem, externalSystemModuleVersion, linkedProjectPath, linkedProjectId,
                                             rootProjectPath, externalSystemModuleGroup, externalSystemModuleType).also { addMetaData(it, snapshot) }
  }
}

class ExternalSystemModuleOptionsEntity(
  val externalSystem: String?,
  val externalSystemModuleVersion: String?,

  val linkedProjectPath: String?,
  val linkedProjectId: String?,
  val rootProjectPath: String?,

  val externalSystemModuleGroup: String?,
  val externalSystemModuleType: String?
) : PTypedEntity() {
  val module: ModuleEntity by OneToOneChild.HardRef.NotNull(ModuleEntity::class, true)
}


val ModuleEntity.externalSystemOptions: ExternalSystemModuleOptionsEntity?
  get() = referrers(ExternalSystemModuleOptionsEntity::module).firstOrNull()

@Suppress("unused")
class FacetEntityData : PEntityData<FacetEntity>() {
  lateinit var name: String
  lateinit var facetType: String
  var configurationXmlTag: String? = null

  override fun createEntity(snapshot: TypedEntityStorage): FacetEntity = FacetEntity(name, facetType, configurationXmlTag).also { addMetaData(it, snapshot) }
}

class FacetEntity(
  val name: String,
  val facetType: String,
  val configurationXmlTag: String?
) : TypedEntityWithPersistentId, PTypedEntity() {
  val module: ModuleEntity by ManyToOne.HardRef.NotNull(ModuleEntity::class)

  val underlyingFacet: FacetEntity? by OneToOneChild.HardRef.Nullable(FacetEntity::class, true)

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

@Suppress("unused")
class ArtifactEntityData : PEntityData<ArtifactEntity>() {
  lateinit var name: String
  lateinit var artifactType: String
  var includeInProjectBuild: Boolean = false
  lateinit var outputUrl: VirtualFileUrl

  override fun createEntity(snapshot: TypedEntityStorage): ArtifactEntity {
    return ArtifactEntity(name, artifactType, includeInProjectBuild, outputUrl).also { addMetaData(it, snapshot) }
  }
}

class ArtifactEntity(
  val name: String,
  val artifactType: String,
  val includeInProjectBuild: Boolean,
  val outputUrl: VirtualFileUrl
) : TypedEntityWithPersistentId, PTypedEntity() {
  val rootElement: CompositePackagingElementEntity by OneToAbstractOneChild.HardRef(CompositePackagingElementEntity::class)

  override fun persistentId(): ArtifactId = ArtifactId(name)

  val customProperties: Sequence<ArtifactPropertiesEntity> by OneToMany.HardRef(ArtifactPropertiesEntity::class, false)
}

@Suppress("unused")
class ArtifactPropertiesEntityData : PEntityData<ArtifactPropertiesEntity>() {
  lateinit var providerType: String
  var propertiesXmlTag: String? = null

  override fun createEntity(snapshot: TypedEntityStorage): ArtifactPropertiesEntity {
    return ArtifactPropertiesEntity(providerType, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class ArtifactPropertiesEntity(
  val providerType: String,
  val propertiesXmlTag: String?
) : PTypedEntity() {
  val artifact: ArtifactEntity by ManyToOne.HardRef.NotNull(ArtifactEntity::class)
}

abstract class PackagingElementEntity : PTypedEntity()

abstract class CompositePackagingElementEntity : PackagingElementEntity() {
  val children: Sequence<PackagingElementEntity> by OneToAbstractMany.HardRef(PackagingElementEntity::class)
}

@Suppress("unused")
class DirectoryPackagingElementEntityData : PEntityData<DirectoryPackagingElementEntity>() {
  lateinit var directoryName: String

  override fun createEntity(snapshot: TypedEntityStorage): DirectoryPackagingElementEntity {
    return DirectoryPackagingElementEntity(directoryName).also { addMetaData(it, snapshot) }
  }
}

class DirectoryPackagingElementEntity(
  val directoryName: String
) : CompositePackagingElementEntity()

@Suppress("unused")
class ArchivePackagingElementEntityData : PEntityData<ArchivePackagingElementEntity>() {
  lateinit var fileName: String

  override fun createEntity(snapshot: TypedEntityStorage): ArchivePackagingElementEntity {
    return ArchivePackagingElementEntity(fileName).also { addMetaData(it, snapshot) }
  }
}

class ArchivePackagingElementEntity(val fileName: String) : CompositePackagingElementEntity()

@Suppress("unused")
class ArtifactRootElementEntityData : PEntityData<ArtifactRootElementEntity>() {
  override fun createEntity(snapshot: TypedEntityStorage): ArtifactRootElementEntity {
    return ArtifactRootElementEntity().also { addMetaData(it, snapshot) }.also { addMetaData(it, snapshot) }
  }
}

class ArtifactRootElementEntity : CompositePackagingElementEntity()

@Suppress("unused")
class ArtifactOutputPackagingElementEntityData : PEntityData<ArtifactOutputPackagingElementEntity>(), PSoftLinkable {
  lateinit var artifact: ArtifactId

  override fun getLinks(): List<PersistentEntityId<*>> = listOf(artifact)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    if (oldLink != artifact) return false
    this.artifact = newLink as ArtifactId
    return true
  }

  override fun createEntity(snapshot: TypedEntityStorage): ArtifactOutputPackagingElementEntity {
    return ArtifactOutputPackagingElementEntity(artifact).also { addMetaData(it, snapshot) }
  }
}

class ArtifactOutputPackagingElementEntity(
  val artifact: ArtifactId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleOutputPackagingElementEntityData : PEntityData<ModuleOutputPackagingElementEntity>(), PSoftLinkable {
  lateinit var module: ModuleId

  override fun getLinks(): List<PersistentEntityId<*>> = listOf(module)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: TypedEntityStorage): ModuleOutputPackagingElementEntity {
    return ModuleOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleOutputPackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class LibraryFilesPackagingElementEntityData : PEntityData<LibraryFilesPackagingElementEntity>(), PSoftLinkable {
  lateinit var library: LibraryId

  override fun getLinks(): List<PersistentEntityId<*>> {
    val tableId = library.tableId
    if (tableId is LibraryTableId.ModuleLibraryTableId) return listOf(library, tableId.moduleId)
    return listOf(library)
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    if (oldLink == library) {
      this.library = newLink as LibraryId
      return true
    }

    val tableId = library.tableId
    if (tableId is LibraryTableId.ModuleLibraryTableId && tableId.moduleId == oldLink) {
      val oldLibrary = library
      this.library = library.copy(tableId = tableId.copy(moduleId = newLink as ModuleId))
      affectedIds += oldLibrary to this.library
    }

    return false
  }

  override fun createEntity(snapshot: TypedEntityStorage): LibraryFilesPackagingElementEntity {
    return LibraryFilesPackagingElementEntity(library).also { addMetaData(it, snapshot) }
  }
}

class LibraryFilesPackagingElementEntity(
  val library: LibraryId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleSourcePackagingElementEntityData : PackagingElementEntity() {
  lateinit var module: ModuleId
}

class ModuleSourcePackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleTestOutputPackagingElementEntityData : PEntityData<ModuleTestOutputPackagingElementEntity>(), PSoftLinkable {
  lateinit var module: ModuleId

  override fun getLinks(): List<PersistentEntityId<*>> = listOf(module)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>,
                          affectedIds: MutableList<Pair<PersistentEntityId<*>, PersistentEntityId<*>>>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: TypedEntityStorage): ModuleTestOutputPackagingElementEntity {
    return ModuleTestOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleTestOutputPackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class DirectoryCopyPackagingElementEntityData : PEntityData<DirectoryCopyPackagingElementEntity>() {
  lateinit var directory: VirtualFileUrl
  override fun createEntity(snapshot: TypedEntityStorage): DirectoryCopyPackagingElementEntity {
    return DirectoryCopyPackagingElementEntity(directory).also { addMetaData(it, snapshot) }
  }
}

class DirectoryCopyPackagingElementEntity(
  val directory: VirtualFileUrl
) : PackagingElementEntity()

@Suppress("unused")
class ExtractedDirectoryPackagingElementEntityData : PEntityData<ExtractedDirectoryPackagingElementEntity>() {
  lateinit var archive: VirtualFileUrl
  lateinit var pathInArchive: String

  override fun createEntity(snapshot: TypedEntityStorage): ExtractedDirectoryPackagingElementEntity {
    return ExtractedDirectoryPackagingElementEntity(archive, pathInArchive).also { addMetaData(it, snapshot) }
  }
}

class ExtractedDirectoryPackagingElementEntity(
  val archive: VirtualFileUrl,
  val pathInArchive: String
) : PackagingElementEntity()

@Suppress("unused")
class FileCopyPackagingElementEntityData : PEntityData<FileCopyPackagingElementEntity>() {
  lateinit var file: VirtualFileUrl
  var renamedOutputFileName: String? = null
  override fun createEntity(snapshot: TypedEntityStorage): FileCopyPackagingElementEntity {
    return FileCopyPackagingElementEntity(file, renamedOutputFileName).also { addMetaData(it, snapshot) }
  }
}

class FileCopyPackagingElementEntity(
  val file: VirtualFileUrl,
  val renamedOutputFileName: String?
) : PackagingElementEntity()

@Suppress("unused")
class CustomPackagingElementEntityData : PEntityData<CustomPackagingElementEntity>() {
  lateinit var typeId: String
  lateinit var propertiesXmlTag: String

  override fun createEntity(snapshot: TypedEntityStorage): CustomPackagingElementEntity {
    return CustomPackagingElementEntity(typeId, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class CustomPackagingElementEntity(
  val typeId: String,
  val propertiesXmlTag: String
) : PackagingElementEntity()