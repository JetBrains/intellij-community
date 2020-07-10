// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.SoftLinkable
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.references.*
import java.io.Serializable

/**
 * A prototype of implementation of the current (legacy) project model via [WorkspaceEntity]. It uses similar concepts to simplify implementation
 * of the bridge to the current model. Some peculiarities are fixed though:
 * * source roots are moved out from content entries, content roots are stored separately in a module;
 * * module libraries are stored in a real library table, unnamed libraries aren't allowed;
 * * 'jar directory' flag is stored in a library root itself;
 * * SDKs are represented as [LibraryEntity] with additional properties specified in [SdkEntity] attached to it.
 */

@Suppress("unused")
class ModuleEntityData : WorkspaceEntityData.WithCalculablePersistentId<ModuleEntity>(), SoftLinkable {
  lateinit var name: String
  var type: String? = null
  lateinit var dependencies: List<ModuleDependencyItem>

  @ExperimentalStdlibApi
  override fun getLinks(): Set<PersistentEntityId<*>> {
    return buildSet {
      dependencies.forEach { dependency ->
        when (dependency) {
          is ModuleDependencyItem.Exportable.ModuleDependency -> this.add(dependency.module)
          is ModuleDependencyItem.Exportable.LibraryDependency -> this.add(dependency.library)
          else -> Unit
        }
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    var changed = false
    val res = dependencies.map { dependency ->
      when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> {
          if (dependency.module == oldLink) {
            changed = true
            dependency.copy(module = newLink as ModuleId)
          }
          else dependency
        }
        is ModuleDependencyItem.Exportable.LibraryDependency -> {
          if (dependency.library == oldLink) {
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

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleEntity = ModuleEntity(name, type, dependencies).also {
    addMetaData(it, snapshot)
  }

  override fun persistentId(): ModuleId = ModuleId(name)
}

class ModuleEntity(
  val name: String,
  val type: String?,
  val dependencies: List<ModuleDependencyItem>
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {

  override fun persistentId(): ModuleId = ModuleId(name)

  val sourceRoots: Sequence<SourceRootEntity> by sourceRootDelegate

  val contentRoots: Sequence<ContentRootEntity> by contentRootDelegate

  val customImlData: ModuleCustomImlDataEntity? by moduleImlDelegate

  val groupPath: ModuleGroupPathEntity? by moduleGroupDelegate

  val javaSettings: JavaModuleSettingsEntity? by javaSettingsDelegate

  companion object {
    val sourceRootDelegate = OneToMany<ModuleEntity, SourceRootEntity>(SourceRootEntity::class.java, false)
    val contentRootDelegate = OneToMany<ModuleEntity, ContentRootEntity>(ContentRootEntity::class.java, false)
    val moduleImlDelegate = OneToOneParent.Nullable<ModuleEntity, ModuleCustomImlDataEntity>(ModuleCustomImlDataEntity::class.java, false)
    val moduleGroupDelegate = OneToOneParent.Nullable<ModuleEntity, ModuleGroupPathEntity>(ModuleGroupPathEntity::class.java, false)
    val javaSettingsDelegate = OneToOneParent.Nullable<ModuleEntity, JavaModuleSettingsEntity>(JavaModuleSettingsEntity::class.java, false)
  }
}

@Suppress("unused")
class JavaModuleSettingsEntityData : WorkspaceEntityData<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean = false
  var excludeOutput: Boolean = false
  var compilerOutput: VirtualFileUrl? = null
  var compilerOutputForTests: VirtualFileUrl? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaModuleSettingsEntity {
    return JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, compilerOutput, compilerOutputForTests)
      .also { addMetaData(it, snapshot) }
  }
}

class JavaModuleSettingsEntity(
  val inheritedCompilerOutput: Boolean,
  val excludeOutput: Boolean,
  val compilerOutput: VirtualFileUrl?,
  val compilerOutputForTests: VirtualFileUrl?
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, JavaModuleSettingsEntity>(ModuleEntity::class.java)
  }
}

@Suppress("unused")
class ModuleCustomImlDataEntityData : WorkspaceEntityData<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? = null
  lateinit var customModuleOptions: Map<String, String>

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleCustomImlDataEntity {
    return ModuleCustomImlDataEntity(rootManagerTagCustomData, customModuleOptions).also { addMetaData(it, snapshot) }
  }
}

class ModuleCustomImlDataEntity(
  val rootManagerTagCustomData: String?,
  val customModuleOptions: Map<String, String>
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, ModuleCustomImlDataEntity>(ModuleEntity::class.java)
  }
}

@Suppress("unused")
class ModuleGroupPathEntityData : WorkspaceEntityData<ModuleGroupPathEntity>() {
  lateinit var path: List<String>

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleGroupPathEntity = ModuleGroupPathEntity(path).also {
    addMetaData(it, snapshot)
  }
}

class ModuleGroupPathEntity(
  val path: List<String>
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, ModuleGroupPathEntity>(ModuleEntity::class.java)
  }
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
class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  var tests: Boolean = false
  lateinit var rootType: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): SourceRootEntity = SourceRootEntity(url, tests, rootType).also {
    addMetaData(it, snapshot)
  }
}

open class SourceRootEntity(
  val url: VirtualFileUrl,
  val tests: Boolean,
  val rootType: String
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate
  val contentRoot: ContentRootEntity by contentRootDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, SourceRootEntity>(ModuleEntity::class.java)
    val contentRootDelegate = ManyToOne.NotNull<ContentRootEntity, SourceRootEntity>(ContentRootEntity::class.java)
  }
}

@Suppress("unused")
class JavaSourceRootEntityData : WorkspaceEntityData<JavaSourceRootEntity>() {
  var generated: Boolean = false
  lateinit var packagePrefix: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaSourceRootEntity = JavaSourceRootEntity(generated, packagePrefix).also {
    addMetaData(it, snapshot)
  }
}

class JavaSourceRootEntity(
  val generated: Boolean,
  val packagePrefix: String
) : WorkspaceEntityBase() {
  val sourceRoot: SourceRootEntity by sourceRootDelegate

  companion object {
    val sourceRootDelegate = ManyToOne.NotNull<SourceRootEntity, JavaSourceRootEntity>(SourceRootEntity::class.java)
  }
}

fun SourceRootEntity.asJavaSourceRoot(): JavaSourceRootEntity? = referrers(JavaSourceRootEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class JavaResourceRootEntityData : WorkspaceEntityData<JavaResourceRootEntity>() {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaResourceRootEntity = JavaResourceRootEntity(generated,
                                                                                                               relativeOutputPath).also {
    addMetaData(it, snapshot)
  }
}

class JavaResourceRootEntity(
  val generated: Boolean,
  val relativeOutputPath: String
) : WorkspaceEntityBase() {
  val sourceRoot: SourceRootEntity by sourceRootDelegate

  companion object {
    val sourceRootDelegate = ManyToOne.NotNull<SourceRootEntity, JavaResourceRootEntity>(SourceRootEntity::class.java)
  }
}

fun SourceRootEntity.asJavaResourceRoot() = referrers(JavaResourceRootEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>() {
  lateinit var propertiesXmlTag: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): CustomSourceRootPropertiesEntity {
    return CustomSourceRootPropertiesEntity(propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class CustomSourceRootPropertiesEntity(
  val propertiesXmlTag: String
) : WorkspaceEntityBase() {
  val sourceRoot: SourceRootEntity by sourceRootDelegate

  companion object {
    val sourceRootDelegate = ManyToOne.NotNull<SourceRootEntity, CustomSourceRootPropertiesEntity>(SourceRootEntity::class.java)
  }
}

fun SourceRootEntity.asCustomSourceRoot() = referrers(CustomSourceRootPropertiesEntity::sourceRoot).firstOrNull()

@Suppress("unused")
class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var excludedUrls: List<VirtualFileUrl>
  lateinit var excludedPatterns: List<String>

  override fun createEntity(snapshot: WorkspaceEntityStorage): ContentRootEntity {
    return ContentRootEntity(url, excludedUrls, excludedPatterns).also { addMetaData(it, snapshot) }
  }
}

open class ContentRootEntity(
  val url: VirtualFileUrl,
  val excludedUrls: List<VirtualFileUrl>,
  val excludedPatterns: List<String>
) : WorkspaceEntityBase() {
  open val module: ModuleEntity by moduleDelegate
  val sourceRoots: Sequence<SourceRootEntity> by sourceRootDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, ContentRootEntity>(ModuleEntity::class.java)
    val sourceRootDelegate = OneToMany<ContentRootEntity, SourceRootEntity>(SourceRootEntity::class.java, false)
  }
}

class FakeContentRootEntity(url: VirtualFileUrl, moduleEntity: ModuleEntity) : ContentRootEntity(url, emptyList(), emptyList()) {
  override val module: ModuleEntity = moduleEntity
  override var entitySource: EntitySource = moduleEntity.entitySource
  override fun hasEqualProperties(e: WorkspaceEntity): Boolean = throw UnsupportedOperationException()
  override fun <R : WorkspaceEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> = throw UnsupportedOperationException()
}

/**
 * This entity stores order of artifacts in file. This is needed to ensure that source roots are saved in the same order to avoid
 * unnecessary modifications of file.
 */
@Suppress("unused")
class SourceRootOrderEntityData : WorkspaceEntityData<SourceRootOrderEntity>() {
  lateinit var orderOfSourceRoots: List<VirtualFileUrl>
  override fun createEntity(snapshot: WorkspaceEntityStorage): SourceRootOrderEntity {
    return SourceRootOrderEntity(orderOfSourceRoots).also { addMetaData(it, snapshot) }
  }
}

class SourceRootOrderEntity(
  var orderOfSourceRoots: List<VirtualFileUrl>
) : WorkspaceEntityBase() {
  val contentRootEntity: ContentRootEntity by contentRootDelegate

  companion object {
    val contentRootDelegate = OneToOneChild.NotNull<SourceRootOrderEntity, ContentRootEntity>(ContentRootEntity::class.java, true)
  }
}

class ModifiableSourceRootOrderEntity : ModifiableWorkspaceEntityBase<SourceRootOrderEntity>() {
  var orderOfSourceRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()

  var contentRootEntity: ContentRootEntity by MutableOneToOneChild.NotNull(SourceRootOrderEntity::class.java, ContentRootEntity::class.java,
                                                                           true)
}

fun ContentRootEntity.getSourceRootOrder() = referrers(SourceRootOrderEntity::contentRootEntity).firstOrNull()

fun ModuleEntity.getModuleLibraries(storage: WorkspaceEntityStorage): Sequence<LibraryEntity> {
  return storage.entities(
    LibraryEntity::class.java).filter { (it.persistentId().tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId?.name == name }
}

val WorkspaceEntityStorage.projectLibraries
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
class LibraryEntityData : WorkspaceEntityData.WithCalculablePersistentId<LibraryEntity>(), SoftLinkable {
  lateinit var tableId: LibraryTableId
  lateinit var name: String
  lateinit var roots: List<LibraryRoot>
  lateinit var excludedRoots: List<VirtualFileUrl>

  override fun getLinks(): Set<PersistentEntityId<*>> {
    val id = tableId
    return if (id is LibraryTableId.ModuleLibraryTableId) setOf(id.moduleId) else emptySet()
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    val id = tableId
    return if (id is LibraryTableId.ModuleLibraryTableId && id.moduleId == oldLink) {
      this.tableId = id.copy(moduleId = newLink as ModuleId)
      true
    }
    else false
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryEntity {
    return LibraryEntity(tableId, name, roots, excludedRoots).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): LibraryId = LibraryId(name, tableId)
}

open class LibraryEntity(
  open val tableId: LibraryTableId,
  val name: String,
  val roots: List<LibraryRoot>,
  val excludedRoots: List<VirtualFileUrl>
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): LibraryId = LibraryId(name, tableId)
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
class LibraryPropertiesEntityData : WorkspaceEntityData<LibraryPropertiesEntity>() {
  lateinit var libraryType: String
  var propertiesXmlTag: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryPropertiesEntity {
    return LibraryPropertiesEntity(libraryType, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class LibraryPropertiesEntity(
  val libraryType: String,
  val propertiesXmlTag: String?
) : WorkspaceEntityBase() {
  val library: LibraryEntity by libraryDelegate

  companion object {
    val libraryDelegate = OneToOneChild.NotNull<LibraryPropertiesEntity, LibraryEntity>(LibraryEntity::class.java, true)
  }
}

fun LibraryEntity.getCustomProperties() = referrers(LibraryPropertiesEntity::library).firstOrNull()

@Suppress("unused")
class SdkEntityData : WorkspaceEntityData<SdkEntity>() {
  lateinit var homeUrl: VirtualFileUrl

  override fun createEntity(snapshot: WorkspaceEntityStorage): SdkEntity = SdkEntity(homeUrl).also { addMetaData(it, snapshot) }
}

class SdkEntity(
  val homeUrl: VirtualFileUrl
) : WorkspaceEntityBase() {
  val library: LibraryEntity by libraryDelegate

  companion object {
    val libraryDelegate = OneToOneChild.NotNull<SdkEntity, LibraryEntity>(LibraryEntity::class.java, true)
  }
}

@Suppress("unused")
class ExternalSystemModuleOptionsEntityData : WorkspaceEntityData<ExternalSystemModuleOptionsEntity>() {
  var externalSystem: String? = null
  var externalSystemModuleVersion: String? = null

  var linkedProjectPath: String? = null
  var linkedProjectId: String? = null
  var rootProjectPath: String? = null

  var externalSystemModuleGroup: String? = null
  var externalSystemModuleType: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): ExternalSystemModuleOptionsEntity {
    return ExternalSystemModuleOptionsEntity(externalSystem, externalSystemModuleVersion, linkedProjectPath, linkedProjectId,
                                             rootProjectPath, externalSystemModuleGroup, externalSystemModuleType).also {
      addMetaData(it, snapshot)
    }
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
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = OneToOneChild.NotNull<ExternalSystemModuleOptionsEntity, ModuleEntity>(ModuleEntity::class.java, true)
  }
}


val ModuleEntity.externalSystemOptions: ExternalSystemModuleOptionsEntity?
  get() = referrers(ExternalSystemModuleOptionsEntity::module).firstOrNull()

@Suppress("unused")
class FacetEntityData : WorkspaceEntityData.WithCalculablePersistentId<FacetEntity>(), SoftLinkable {
  lateinit var name: String
  lateinit var facetType: String
  var configurationXmlTag: String? = null
  lateinit var moduleId: ModuleId

  override fun createEntity(snapshot: WorkspaceEntityStorage): FacetEntity {
    return FacetEntity(name, facetType, configurationXmlTag, moduleId).also { addMetaData(it, snapshot) }
  }

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(moduleId)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (moduleId != oldLink) return false

    moduleId = newLink as ModuleId
    return true
  }

  override fun persistentId(): PersistentEntityId<*> = FacetId(name, facetType, moduleId)
}

class FacetEntity(
  val name: String,
  val facetType: String,
  val configurationXmlTag: String?,
  val moduleId: ModuleId
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  val underlyingFacet: FacetEntity? by facetDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, FacetEntity>(ModuleEntity::class.java)
    val facetDelegate = OneToOneChild.Nullable<FacetEntity, FacetEntity>(FacetEntity::class.java, true)
  }

  override fun persistentId(): FacetId = FacetId(name, facetType, moduleId)
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
class ArtifactEntityData : WorkspaceEntityData.WithCalculablePersistentId<ArtifactEntity>() {
  lateinit var name: String
  lateinit var artifactType: String
  var includeInProjectBuild: Boolean = false
  lateinit var outputUrl: VirtualFileUrl

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactEntity {
    return ArtifactEntity(name, artifactType, includeInProjectBuild, outputUrl).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): ArtifactId = ArtifactId(name)
}

class ArtifactEntity(
  val name: String,
  val artifactType: String,
  val includeInProjectBuild: Boolean,
  val outputUrl: VirtualFileUrl
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): ArtifactId = ArtifactId(name)

  val rootElement: CompositePackagingElementEntity by rootElementDelegate

  val customProperties: Sequence<ArtifactPropertiesEntity> by customPropertiesDelegate

  companion object {
    val rootElementDelegate = OneToAbstractOneChild<CompositePackagingElementEntity, ArtifactEntity>(
      CompositePackagingElementEntity::class.java)
    val customPropertiesDelegate = OneToMany<ArtifactEntity, ArtifactPropertiesEntity>(ArtifactPropertiesEntity::class.java, false)
  }
}

@Suppress("unused")
class ArtifactPropertiesEntityData : WorkspaceEntityData<ArtifactPropertiesEntity>() {
  lateinit var providerType: String
  var propertiesXmlTag: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactPropertiesEntity {
    return ArtifactPropertiesEntity(providerType, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class ArtifactPropertiesEntity(
  val providerType: String,
  val propertiesXmlTag: String?
) : WorkspaceEntityBase() {
  val artifact: ArtifactEntity by artifactDelegate

  companion object {
    val artifactDelegate = ManyToOne.NotNull<ArtifactEntity, ArtifactPropertiesEntity>(ArtifactEntity::class.java)
  }
}

abstract class PackagingElementEntity : WorkspaceEntityBase()

abstract class CompositePackagingElementEntity : PackagingElementEntity() {
  val children: Sequence<PackagingElementEntity> by OneToAbstractMany(PackagingElementEntity::class.java)
}

@Suppress("unused")
class DirectoryPackagingElementEntityData : WorkspaceEntityData<DirectoryPackagingElementEntity>() {
  lateinit var directoryName: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): DirectoryPackagingElementEntity {
    return DirectoryPackagingElementEntity(directoryName).also { addMetaData(it, snapshot) }
  }
}

class DirectoryPackagingElementEntity(
  val directoryName: String
) : CompositePackagingElementEntity()

@Suppress("unused")
class ArchivePackagingElementEntityData : WorkspaceEntityData<ArchivePackagingElementEntity>() {
  lateinit var fileName: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArchivePackagingElementEntity {
    return ArchivePackagingElementEntity(fileName).also { addMetaData(it, snapshot) }
  }
}

class ArchivePackagingElementEntity(val fileName: String) : CompositePackagingElementEntity()

@Suppress("unused")
class ArtifactRootElementEntityData : WorkspaceEntityData<ArtifactRootElementEntity>() {
  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactRootElementEntity {
    return ArtifactRootElementEntity().also { addMetaData(it, snapshot) }.also { addMetaData(it, snapshot) }
  }
}

class ArtifactRootElementEntity : CompositePackagingElementEntity()

@Suppress("unused")
class ArtifactOutputPackagingElementEntityData : WorkspaceEntityData<ArtifactOutputPackagingElementEntity>(), SoftLinkable {
  lateinit var artifact: ArtifactId

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(artifact)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (oldLink != artifact) return false
    this.artifact = newLink as ArtifactId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactOutputPackagingElementEntity {
    return ArtifactOutputPackagingElementEntity(artifact).also { addMetaData(it, snapshot) }
  }
}

class ArtifactOutputPackagingElementEntity(
  val artifact: ArtifactId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleOutputPackagingElementEntityData : WorkspaceEntityData<ModuleOutputPackagingElementEntity>(), SoftLinkable {
  lateinit var module: ModuleId

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(module)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleOutputPackagingElementEntity {
    return ModuleOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleOutputPackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class LibraryFilesPackagingElementEntityData : WorkspaceEntityData<LibraryFilesPackagingElementEntity>(), SoftLinkable {
  lateinit var library: LibraryId

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(library)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (oldLink == library) {
      this.library = newLink as LibraryId
      return true
    }

    return false
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryFilesPackagingElementEntity {
    return LibraryFilesPackagingElementEntity(library).also { addMetaData(it, snapshot) }
  }
}

class LibraryFilesPackagingElementEntity(
  val library: LibraryId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleSourcePackagingElementEntityData : WorkspaceEntityData<ModuleSourcePackagingElementEntity>(), SoftLinkable {
  lateinit var module: ModuleId

  override fun getLinks(): Set<PersistentEntityId<*>> {
    return setOf(module)
  }

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleSourcePackagingElementEntity {
    return ModuleSourcePackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleSourcePackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class ModuleTestOutputPackagingElementEntityData : WorkspaceEntityData<ModuleTestOutputPackagingElementEntity>(), SoftLinkable {
  lateinit var module: ModuleId

  override fun getLinks(): Set<PersistentEntityId<*>> = setOf(module)

  override fun updateLink(oldLink: PersistentEntityId<*>,
                          newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleTestOutputPackagingElementEntity {
    return ModuleTestOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleTestOutputPackagingElementEntity(
  val module: ModuleId
) : PackagingElementEntity()

@Suppress("unused")
class DirectoryCopyPackagingElementEntityData : WorkspaceEntityData<DirectoryCopyPackagingElementEntity>() {
  lateinit var directory: VirtualFileUrl
  override fun createEntity(snapshot: WorkspaceEntityStorage): DirectoryCopyPackagingElementEntity {
    return DirectoryCopyPackagingElementEntity(directory).also { addMetaData(it, snapshot) }
  }
}

class DirectoryCopyPackagingElementEntity(
  val directory: VirtualFileUrl
) : PackagingElementEntity()

@Suppress("unused")
class ExtractedDirectoryPackagingElementEntityData : WorkspaceEntityData<ExtractedDirectoryPackagingElementEntity>() {
  lateinit var archive: VirtualFileUrl
  lateinit var pathInArchive: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): ExtractedDirectoryPackagingElementEntity {
    return ExtractedDirectoryPackagingElementEntity(archive, pathInArchive).also { addMetaData(it, snapshot) }
  }
}

class ExtractedDirectoryPackagingElementEntity(
  val archive: VirtualFileUrl,
  val pathInArchive: String
) : PackagingElementEntity()

@Suppress("unused")
class FileCopyPackagingElementEntityData : WorkspaceEntityData<FileCopyPackagingElementEntity>() {
  lateinit var file: VirtualFileUrl
  var renamedOutputFileName: String? = null
  override fun createEntity(snapshot: WorkspaceEntityStorage): FileCopyPackagingElementEntity {
    return FileCopyPackagingElementEntity(file, renamedOutputFileName).also { addMetaData(it, snapshot) }
  }
}

class FileCopyPackagingElementEntity(
  val file: VirtualFileUrl,
  val renamedOutputFileName: String?
) : PackagingElementEntity()

@Suppress("unused")
class CustomPackagingElementEntityData : WorkspaceEntityData<CustomPackagingElementEntity>() {
  lateinit var typeId: String
  lateinit var propertiesXmlTag: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): CustomPackagingElementEntity {
    return CustomPackagingElementEntity(typeId, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }
}

class CustomPackagingElementEntity(
  val typeId: String,
  val propertiesXmlTag: String
) : PackagingElementEntity()