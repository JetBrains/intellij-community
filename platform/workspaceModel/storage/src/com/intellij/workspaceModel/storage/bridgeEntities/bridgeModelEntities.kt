// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.openapi.util.NlsSafe
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.*
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileUrlListProperty
import com.intellij.workspaceModel.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.workspaceModel.storage.impl.references.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.io.Serializable

/**
 * A prototype of implementation of the current (legacy) project model via [WorkspaceEntity]. It uses similar concepts to simplify implementation
 * of the bridge to the current model. Some peculiarities are fixed though:
 * * unnamed libraries aren't allowed: module-level libraries without a name get automatically generated names;
 * * 'jar directory' flag is stored in a library root itself;
 */

@Suppress("unused")
class ModuleEntityData : WorkspaceEntityData.WithCalculablePersistentId<ModuleEntity>(), SoftLinkable, WithAssertableConsistency {
  lateinit var name: String
  var type: String? = null
  lateinit var dependencies: List<ModuleDependencyItem>

  override fun getLinks(): Set<PersistentEntityId<*>> {

    return dependencies.mapNotNullTo(HashSet()) { dependency ->
      when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> dependency.module
        is ModuleDependencyItem.Exportable.LibraryDependency -> dependency.library
        else -> null
      }
    }
  }

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    for (dependency in dependencies) {
      when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> index.index(this, dependency.module)
        is ModuleDependencyItem.Exportable.LibraryDependency -> index.index(this, dependency.library)
        else -> Unit
      }
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val mutablePreviousSet = HashSet(prev)

    for (dependency in dependencies) {
      val dep = when (dependency) {
        is ModuleDependencyItem.Exportable.ModuleDependency -> dependency.module
        is ModuleDependencyItem.Exportable.LibraryDependency -> dependency.library
        else -> continue
      }
      val removed = mutablePreviousSet.remove(dep)
      if (!removed) {
        index.index(this, dep)
      }
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
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
    addMetaData(it, snapshot, classId)
  }

  override fun persistentId(): ModuleId = ModuleId(name)

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    this.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>().forEach { libraryDependency ->
      val tableId = libraryDependency.library.tableId
      if (tableId is LibraryTableId.ModuleLibraryTableId) {
        assert(tableId.moduleId.name == this.name)
      }
    }
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ModuleEntityData) return false

    if (name != other.name) return false
    if (type != other.type) return false
    if (dependencies != other.dependencies) return false

    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ModuleEntityData) return false

    if (name != other.name) return false
    if (type != other.type) return false
    if (dependencies != other.dependencies) return false
    if (entitySource != other.entitySource) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (type?.hashCode() ?: 0)
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + entitySource.hashCode()
    return result
  }

  companion object {
    @Transient
    private val classId: Int = ClassToIntConverter.INSTANCE.getInt(ModuleEntity::class.java)
  }
}

class ModuleEntity(
  val name: String,
  val type: String?,
  val dependencies: List<ModuleDependencyItem>
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {

  override fun persistentId(): ModuleId = ModuleId(name)

  val sourceRoots: Sequence<SourceRootEntity>
    get() = contentRoots.flatMap { it.sourceRoots }

  val contentRoots: Sequence<ContentRootEntity> by contentRootDelegate

  val customImlData: ModuleCustomImlDataEntity? by moduleImlDelegate

  val groupPath: ModuleGroupPathEntity? by moduleGroupDelegate

  val javaSettings: JavaModuleSettingsEntity? by javaSettingsDelegate

  companion object {
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
  var languageLevelId: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaModuleSettingsEntity {
    return JavaModuleSettingsEntity(inheritedCompilerOutput, excludeOutput, compilerOutput, compilerOutputForTests, languageLevelId)
      .also { addMetaData(it, snapshot) }
  }
}

class JavaModuleSettingsEntity(
  val inheritedCompilerOutput: Boolean,
  val excludeOutput: Boolean,
  val compilerOutput: VirtualFileUrl?,
  val compilerOutputForTests: VirtualFileUrl?,
  val languageLevelId: String?
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate

  companion object {
    val moduleDelegate = OneToOneChild.NotNull<JavaModuleSettingsEntity, ModuleEntity>(ModuleEntity::class.java)
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
    val moduleDelegate = OneToOneChild.NotNull< ModuleCustomImlDataEntity, ModuleEntity>(ModuleEntity::class.java)
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
  override val presentableName: String
    get() = name

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ModuleId) return false
    return name == other.name
  }

  override fun hashCode(): Int  = name.hashCode()
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
  data class SdkDependency(@NlsSafe val sdkName: String, val sdkType: String) : ModuleDependencyItem()

  object InheritedSdkDependency : ModuleDependencyItem()
  object ModuleSourceDependency : ModuleDependencyItem()
  enum class DependencyScope { COMPILE, TEST, RUNTIME, PROVIDED }
}

@Suppress("unused")
class SourceRootEntityData : WorkspaceEntityData<SourceRootEntity>() {
  lateinit var url: VirtualFileUrl
  lateinit var rootType: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): SourceRootEntity = SourceRootEntity(url, rootType).also {
    addMetaData(it, snapshot)
  }
}

class SourceRootEntity(
  val url: VirtualFileUrl,
  val rootType: String
) : WorkspaceEntityBase() {
  val contentRoot: ContentRootEntity by contentRootDelegate

  companion object {
    val contentRootDelegate = ManyToOne.NotNull<ContentRootEntity, SourceRootEntity>(ContentRootEntity::class.java)
  }
}

@Suppress("unused")
class JavaSourceRootEntityData : WorkspaceEntityData<JavaSourceRootEntity>(), WithAssertableConsistency {
  var generated: Boolean = false
  lateinit var packagePrefix: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaSourceRootEntity = JavaSourceRootEntity(generated, packagePrefix).also {
    addMetaData(it, snapshot)
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    val thisEntity = this.createEntity(storage)
    val attachedSourceRoot = thisEntity.sourceRoot
    assert(thisEntity.entitySource == attachedSourceRoot.entitySource) {
      """
      |Entity source of source root entity and it's java source root entity differs. 
      |   Source root entity source: ${attachedSourceRoot.entitySource}
      |   Java source root source: ${thisEntity.entitySource}
      |   Source root entity: $attachedSourceRoot
      |   Java root entity: $thisEntity
      """.trimMargin()
    }
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
class JavaResourceRootEntityData : WorkspaceEntityData<JavaResourceRootEntity>(), WithAssertableConsistency {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): JavaResourceRootEntity = JavaResourceRootEntity(generated,
                                                                                                               relativeOutputPath).also {
    addMetaData(it, snapshot)
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    val thisEntity = this.createEntity(storage)
    val attachedSourceRoot = thisEntity.sourceRoot
    assert(thisEntity.entitySource == attachedSourceRoot.entitySource) {
      """
      |Entity source of source root entity and it's java resource root entity differs. 
      |   Source root entity source: ${attachedSourceRoot.entitySource}
      |   Java resource root source: ${thisEntity.entitySource}
      |   Source root entity: $attachedSourceRoot
      |   Java root entity: $thisEntity
      """.trimMargin()
    }
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
class CustomSourceRootPropertiesEntityData : WorkspaceEntityData<CustomSourceRootPropertiesEntity>(), WithAssertableConsistency {
  lateinit var propertiesXmlTag: String
  override fun createEntity(snapshot: WorkspaceEntityStorage): CustomSourceRootPropertiesEntity {
    return CustomSourceRootPropertiesEntity(propertiesXmlTag).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    val thisEntity = this.createEntity(storage)
    val attachedSourceRoot = thisEntity.sourceRoot
    assert(thisEntity.entitySource == attachedSourceRoot.entitySource) {
      """
      |Entity source of source root entity and it's CustomSourceRootProperties entity differs. 
      |   Source root entity source: ${attachedSourceRoot.entitySource}
      |   CustomSourceRootProperties source: ${thisEntity.entitySource}
      |   Source root entity: $attachedSourceRoot
      |   CustomSourceRootProperties entity: $thisEntity
      """.trimMargin()
    }
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
class ContentRootEntityData : WorkspaceEntityData<ContentRootEntity>(), WithAssertableConsistency {
  lateinit var url: VirtualFileUrl
  lateinit var excludedUrls: List<VirtualFileUrl>
  lateinit var excludedPatterns: List<String>

  override fun createEntity(snapshot: WorkspaceEntityStorage): ContentRootEntity {
    return ContentRootEntity(url, excludedUrls, excludedPatterns).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    // Module can have a different entity source in case of OC

    // The assertion is currently disabled because it fails
    // com.android.tools.idea.gradle.project.sync.GradleSyncProjectComparisonTest.GradleSyncProjectComparisonTestCase.testPsdSampleRenamingModule

    /*
        if (this.entitySource.toString() == "OCEntitySource") return

        val thisEntity = this.createEntity(storage)
        val attachedModule = thisEntity.module

        assert(thisEntity.entitySource == attachedModule.entitySource) {
          """
          |Entity source of content root entity and it's module entity differs.
          |   Module entity source: ${attachedModule.entitySource}
          |   Content root source: ${thisEntity.entitySource}
          |   Module entity: $attachedModule
          |   Content root entity: $thisEntity
          """.trimMargin()
        }
    */
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContentRootEntityData) return false

    if (url != other.url) return false
    if (excludedUrls != other.excludedUrls) return false
    if (excludedPatterns != other.excludedPatterns) return false

    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContentRootEntityData) return false

    if (url != other.url) return false
    if (excludedUrls != other.excludedUrls) return false
    if (excludedPatterns != other.excludedPatterns) return false
    if (entitySource != other.entitySource) return false

    return true
  }

  override fun hashCode(): Int {
    var result = url.hashCode()
    result = 31 * result + excludedUrls.hashCode()
    result = 31 * result + excludedPatterns.hashCode()
    result = 31 * result + entitySource.hashCode()
    return result
  }
}

class ContentRootEntity(
  val url: VirtualFileUrl,
  val excludedUrls: List<VirtualFileUrl>,
  val excludedPatterns: List<String>
) : WorkspaceEntityBase() {
  val module: ModuleEntity by moduleDelegate
  val sourceRoots: Sequence<SourceRootEntity> by sourceRootDelegate

  companion object {
    val moduleDelegate = ManyToOne.NotNull<ModuleEntity, ContentRootEntity>(ModuleEntity::class.java)
    val sourceRootDelegate = OneToMany<ContentRootEntity, SourceRootEntity>(SourceRootEntity::class.java, false)
  }
}

/**
 * This entity stores order of artifacts in file. This is needed to ensure that source roots are saved in the same order to avoid
 * unnecessary modifications of file.
 */
@Suppress("unused")
class SourceRootOrderEntityData : WorkspaceEntityData<SourceRootOrderEntity>(), WithAssertableConsistency {
  lateinit var orderOfSourceRoots: List<VirtualFileUrl>
  override fun createEntity(snapshot: WorkspaceEntityStorage): SourceRootOrderEntity {
    return SourceRootOrderEntity(orderOfSourceRoots).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    val thisEntity = this.createEntity(storage)
    val attachedContentRoot = thisEntity.contentRootEntity
    assert(thisEntity.entitySource == attachedContentRoot.entitySource) {
      """
      |Entity source of content root entity and it's SourceRootOrderEntity entity differs. 
      |   Content root entity source: ${attachedContentRoot.entitySource}
      |   SourceRootOrderEntity source: ${thisEntity.entitySource}
      |   Content root entity: $attachedContentRoot
      |   SourceRootOrderEntity entity: $thisEntity
      """.trimMargin()
    }
  }
}

class SourceRootOrderEntity(
  var orderOfSourceRoots: List<VirtualFileUrl>
) : WorkspaceEntityBase() {
  val contentRootEntity: ContentRootEntity by contentRootDelegate

  companion object {
    val contentRootDelegate = OneToOneChild.NotNull<SourceRootOrderEntity, ContentRootEntity>(ContentRootEntity::class.java)
  }
}

class ModifiableSourceRootOrderEntity : ModifiableWorkspaceEntityBase<SourceRootOrderEntity>() {
  var orderOfSourceRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()

  var contentRootEntity: ContentRootEntity by MutableOneToOneChild.NotNull(SourceRootOrderEntity::class.java, ContentRootEntity::class.java
  )
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

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val id = tableId
    if (id is LibraryTableId.ModuleLibraryTableId) {
      index.index(this, id.moduleId)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val id = tableId
    val previous = prev.singleOrNull()
    if (id is LibraryTableId.ModuleLibraryTableId) {
      if (previous != null) {
        if (id.moduleId != previous) {
          index.remove(this, previous)
          index.index(this, id.moduleId)
        }
      }
      else {
        index.index(this, id.moduleId)
      }
    }
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
    return LibraryEntity(tableId, name, roots, excludedRoots).also { addMetaData(it, snapshot, classId) }
  }

  override fun persistentId(): LibraryId = LibraryId(name, tableId)

  companion object {
    @Transient
    private val classId: Int = ClassToIntConverter.INSTANCE.getInt(LibraryEntity::class.java)
  }
}

class LibraryEntity(
  val tableId: LibraryTableId,
  val name: String,
  val roots: List<LibraryRoot>,
  val excludedRoots: List<VirtualFileUrl>
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): LibraryId = LibraryId(name, tableId)
}

data class LibraryId(val name: String, val tableId: LibraryTableId) : PersistentEntityId<LibraryEntity>() {
  override val presentableName: String
    get() = name

  @Transient
  private var codeCache: Int = 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LibraryId) return false

    if (this.codeCache != 0 && other.codeCache != 0 && this.codeCache != other.codeCache) return false
    if (name != other.name) return false
    if (tableId != other.tableId) return false

    return true
  }

  override fun hashCode(): Int {
    if (codeCache != 0) return codeCache
    var result = name.hashCode()
    result = 31 * result + tableId.hashCode()
    this.codeCache = result
    return result
  }
}

data class LibraryRootTypeId(val name: String) : Serializable {
  companion object {
    val COMPILED = LibraryRootTypeId("CLASSES")
    val SOURCES = LibraryRootTypeId("SOURCES")
  }
}

data class LibraryRoot(
  val url: VirtualFileUrl,
  val type: LibraryRootTypeId,
  val inclusionOptions: InclusionOptions = InclusionOptions.ROOT_ITSELF
) : Serializable {

  enum class InclusionOptions {
    ROOT_ITSELF, ARCHIVES_UNDER_ROOT, ARCHIVES_UNDER_ROOT_RECURSIVELY
  }
}

@Suppress("unused")
class LibraryPropertiesEntityData : WorkspaceEntityData<LibraryPropertiesEntity>(), WithAssertableConsistency {
  lateinit var libraryType: String
  var propertiesXmlTag: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): LibraryPropertiesEntity {
    return LibraryPropertiesEntity(libraryType, propertiesXmlTag).also { addMetaData(it, snapshot) }
  }

  override fun assertConsistency(storage: WorkspaceEntityStorage) {
    val propertiesEntity = this.createEntity(storage)
    val attachedLibrary = propertiesEntity.library
    assert(attachedLibrary.entitySource == this.entitySource) { """
      |Entity source of library and it's properties differs. 
      |   Library entity source: ${attachedLibrary.entitySource}
      |   Properties entity source: ${this.entitySource}
      |   Library entity: $attachedLibrary
      |   Properties entity: $propertiesEntity
    """.trimMargin() }
  }
}

class LibraryPropertiesEntity(
  val libraryType: String,
  val propertiesXmlTag: String?
) : WorkspaceEntityBase() {
  val library: LibraryEntity by libraryDelegate

  companion object {
    val libraryDelegate = OneToOneChild.NotNull<LibraryPropertiesEntity, LibraryEntity>(LibraryEntity::class.java)
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
    val libraryDelegate = OneToOneChild.NotNull<SdkEntity, LibraryEntity>(LibraryEntity::class.java)
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
    val moduleDelegate = OneToOneChild.NotNull<ExternalSystemModuleOptionsEntity, ModuleEntity>(ModuleEntity::class.java)
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

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    index.index(this, moduleId)
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    val previous = prev.singleOrNull()
    if (previous != null) {
      if (previous != moduleId) {
        index.remove(this, previous)
        index.index(this, moduleId)
      }
    }
    else {
      index.index(this, moduleId)
    }
  }

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
    val facetDelegate = ManyToOne.Nullable<FacetEntity, FacetEntity>(FacetEntity::class.java)
  }

  override fun persistentId(): FacetId = FacetId(name, facetType, moduleId)
}

data class FacetId(val name: String, val type: String, val parentId: ModuleId) : PersistentEntityId<FacetEntity>() {
  override val presentableName: String
    get() = name
}

val FacetEntity.subFacets: Sequence<FacetEntity>
  get() = referrers(FacetEntity::underlyingFacet)

val ModuleEntity.facets: Sequence<FacetEntity>
  get() = referrers(FacetEntity::module)


data class ArtifactId(val name: String) : PersistentEntityId<ArtifactEntity>() {
  override val presentableName: String
    get() = name
}

@Suppress("unused")
class ArtifactEntityData : WorkspaceEntityData.WithCalculablePersistentId<ArtifactEntity>() {
  lateinit var name: String
  lateinit var artifactType: String
  var includeInProjectBuild: Boolean = false
  var outputUrl: VirtualFileUrl? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactEntity {
    return ArtifactEntity(name, artifactType, includeInProjectBuild, outputUrl).also { addMetaData(it, snapshot) }
  }

  override fun persistentId(): ArtifactId = ArtifactId(name)
}

class ArtifactEntity(
  val name: String,
  val artifactType: String,
  val includeInProjectBuild: Boolean,
  val outputUrl: VirtualFileUrl?
) : WorkspaceEntityWithPersistentId, WorkspaceEntityBase() {
  override fun persistentId(): ArtifactId = ArtifactId(name)

  val rootElement: CompositePackagingElementEntity? by rootElementDelegate

  val customProperties: Sequence<ArtifactPropertiesEntity> by customPropertiesDelegate

  companion object {
    val rootElementDelegate = OneToAbstractOneParent<ArtifactEntity, CompositePackagingElementEntity>(
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
  var artifact: ArtifactId? = null

  override fun getLinks(): Set<PersistentEntityId<*>> = artifact?.let { setOf(it) } ?: emptySet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    artifact?.let {
      index.index(this, it)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    artifact?.let {
      val previous = prev.singleOrNull()
      if (previous != null) {
        if (previous != it) {
          index.remove(this, previous)
          index.index(this, it)
        }
      }
      else {
        index.index(this, it)
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    if (oldLink != artifact) return false
    this.artifact = newLink as ArtifactId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ArtifactOutputPackagingElementEntity {
    return ArtifactOutputPackagingElementEntity(artifact).also { addMetaData(it, snapshot) }
  }
}

class ArtifactOutputPackagingElementEntity(val artifact: ArtifactId?) : PackagingElementEntity()

@Suppress("unused")
class ModuleOutputPackagingElementEntityData : WorkspaceEntityData<ModuleOutputPackagingElementEntity>(), SoftLinkable {
  var module: ModuleId? = null

  override fun getLinks(): Set<PersistentEntityId<*>> = module?.let { setOf(it) } ?: emptySet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      index.index(this, it)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      val previous = prev.singleOrNull()
      if (previous != null) {
        if (previous != it) {
          index.remove(this, previous)
          index.index(this, it)
        }
      }
      else {
        index.index(this, it)
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleOutputPackagingElementEntity {
    return ModuleOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleOutputPackagingElementEntity(val module: ModuleId?) : PackagingElementEntity()

@Suppress("unused")
class LibraryFilesPackagingElementEntityData : WorkspaceEntityData<LibraryFilesPackagingElementEntity>(), SoftLinkable {
  var library: LibraryId? = null

  override fun getLinks(): Set<PersistentEntityId<*>> = library?.let { setOf(it) } ?: emptySet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    library?.let {
      index.index(this, it)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    library?.let {
      val previous = prev.singleOrNull()
      if (previous != null) {
        if (previous != it) {
          index.remove(this, previous)
          index.index(this, it)
        }
      }
      else {
        index.index(this, it)
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
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

class LibraryFilesPackagingElementEntity(val library: LibraryId?) : PackagingElementEntity()

@Suppress("unused")
class ModuleSourcePackagingElementEntityData : WorkspaceEntityData<ModuleSourcePackagingElementEntity>(), SoftLinkable {
  var module: ModuleId? = null

  override fun getLinks(): Set<PersistentEntityId<*>> = module?.let { setOf(it) } ?: emptySet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      index.index(this, it)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      val previous = prev.singleOrNull()
      if (previous != null) {
        if (previous != it) {
          index.remove(this, previous)
          index.index(this, it)
        }
      }
      else {
        index.index(this, it)
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleSourcePackagingElementEntity {
    return ModuleSourcePackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleSourcePackagingElementEntity(val module: ModuleId?) : PackagingElementEntity()

@Suppress("unused")
class ModuleTestOutputPackagingElementEntityData : WorkspaceEntityData<ModuleTestOutputPackagingElementEntity>(), SoftLinkable {
  var module: ModuleId? = null

  override fun getLinks(): Set<PersistentEntityId<*>> = module?.let { setOf(it) } ?: emptySet()

  override fun index(index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      index.index(this, it)
    }
  }

  override fun updateLinksIndex(prev: Set<PersistentEntityId<*>>, index: WorkspaceMutableIndex<PersistentEntityId<*>>) {
    module?.let {
      val previous = prev.singleOrNull()
      if (previous != null) {
        if (previous != it) {
          index.remove(this, previous)
          index.index(this, it)
        }
      }
      else {
        index.index(this, it)
      }
    }
  }

  override fun updateLink(oldLink: PersistentEntityId<*>, newLink: PersistentEntityId<*>): Boolean {
    if (module != oldLink) return false
    this.module = newLink as ModuleId
    return true
  }

  override fun createEntity(snapshot: WorkspaceEntityStorage): ModuleTestOutputPackagingElementEntity {
    return ModuleTestOutputPackagingElementEntity(module).also { addMetaData(it, snapshot) }
  }
}

class ModuleTestOutputPackagingElementEntity(val module: ModuleId?) : PackagingElementEntity()

abstract class FileOrDirectoryPackagingElementEntity(val filePath: VirtualFileUrl) : PackagingElementEntity()

@Suppress("unused")
class DirectoryCopyPackagingElementEntityData : WorkspaceEntityData<DirectoryCopyPackagingElementEntity>() {
  lateinit var filePath: VirtualFileUrl

  override fun createEntity(snapshot: WorkspaceEntityStorage): DirectoryCopyPackagingElementEntity {
    return DirectoryCopyPackagingElementEntity(filePath).also { addMetaData(it, snapshot) }
  }
}

class DirectoryCopyPackagingElementEntity(filePath: VirtualFileUrl) : FileOrDirectoryPackagingElementEntity(filePath)

@Suppress("unused")
class ExtractedDirectoryPackagingElementEntityData : WorkspaceEntityData<ExtractedDirectoryPackagingElementEntity>() {
  lateinit var filePath: VirtualFileUrl
  lateinit var pathInArchive: String

  override fun createEntity(snapshot: WorkspaceEntityStorage): ExtractedDirectoryPackagingElementEntity {
    return ExtractedDirectoryPackagingElementEntity(filePath, pathInArchive).also { addMetaData(it, snapshot) }
  }
}

class ExtractedDirectoryPackagingElementEntity(
  filePath: VirtualFileUrl,
  val pathInArchive: String
) : FileOrDirectoryPackagingElementEntity(filePath)

@Suppress("unused")
class FileCopyPackagingElementEntityData : WorkspaceEntityData<FileCopyPackagingElementEntity>() {
  lateinit var filePath: VirtualFileUrl
  var renamedOutputFileName: String? = null

  override fun createEntity(snapshot: WorkspaceEntityStorage): FileCopyPackagingElementEntity {
    return FileCopyPackagingElementEntity(filePath, renamedOutputFileName).also { addMetaData(it, snapshot) }
  }
}

class FileCopyPackagingElementEntity(
  filePath: VirtualFileUrl,
  val renamedOutputFileName: String?
) : FileOrDirectoryPackagingElementEntity(filePath)

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
) : CompositePackagingElementEntity()
