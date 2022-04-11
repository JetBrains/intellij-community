// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntitiesx

import com.intellij.openapi.diagnostic.logger
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
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

class ModifiableJavaModuleSettingsEntity : ModifiableWorkspaceEntityBase<JavaModuleSettingsEntity>() {
  var inheritedCompilerOutput: Boolean by EntityDataDelegation()
  var excludeOutput: Boolean by EntityDataDelegation()
  var compilerOutput: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var compilerOutputForTests: VirtualFileUrl? by VirtualFileUrlNullableProperty()
  var languageLevelId: String? by EntityDataDelegation()

  var module: ModuleEntity by MutableOneToOneChild.NotNull(JavaModuleSettingsEntity::class.java, ModuleEntity::class.java)
}

class ModifiableModuleCustomImlDataEntity : ModifiableWorkspaceEntityBase<ModuleCustomImlDataEntity>() {
  var rootManagerTagCustomData: String? by EntityDataDelegation()
  var customModuleOptions: MutableMap<String, String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleCustomImlDataEntity::class.java, ModuleEntity::class.java)
  override fun getEntityClass(): Class<ModuleCustomImlDataEntity> {
    return ModuleCustomImlDataEntity::class.java
  }
}

class ModifiableModuleGroupPathEntity : ModifiableWorkspaceEntityBase<ModuleGroupPathEntity>() {
  var path: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableOneToOneChild.NotNull(ModuleGroupPathEntity::class.java, ModuleEntity::class.java)
  override fun getEntityClass(): Class<ModuleGroupPathEntity> {
    return ModuleGroupPathEntity::class.java
  }
}

class ModifiableSourceRootEntity : ModifiableWorkspaceEntityBase<SourceRootEntity>() {
  var contentRoot: ContentRootEntity by MutableManyToOne.NotNull(SourceRootEntity::class.java, ContentRootEntity::class.java)
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var rootType: String by EntityDataDelegation()
}

class ModifiableJavaSourceRootEntity : ModifiableWorkspaceEntityBase<JavaSourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(JavaSourceRootEntity::class.java, SourceRootEntity::class.java)
  var generated: Boolean by EntityDataDelegation()
  var packagePrefix: String by EntityDataDelegation()
  override fun getEntityClass(): Class<JavaSourceRootEntity> {
    return JavaSourceRootEntity::class.java
  }
}

class ModifiableJavaResourceRootEntity : ModifiableWorkspaceEntityBase<JavaResourceRootEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(JavaResourceRootEntity::class.java, SourceRootEntity::class.java)
  var generated: Boolean by EntityDataDelegation()
  var relativeOutputPath: String by EntityDataDelegation()
  override fun getEntityClass(): Class<JavaResourceRootEntity> {
    return JavaResourceRootEntity::class.java
  }
}

class ModifiableCustomSourceRootPropertiesEntity : ModifiableWorkspaceEntityBase<CustomSourceRootPropertiesEntity>() {
  var sourceRoot: SourceRootEntity by MutableManyToOne.NotNull(CustomSourceRootPropertiesEntity::class.java, SourceRootEntity::class.java)
  var propertiesXmlTag: String by EntityDataDelegation()
  override fun getEntityClass(): Class<CustomSourceRootPropertiesEntity> {
    return CustomSourceRootPropertiesEntity::class.java
  }
}

class ModifiableContentRootEntity : ModifiableWorkspaceEntityBase<ContentRootEntity>() {
  var url: VirtualFileUrl by VirtualFileUrlProperty()
  var excludedUrls: List<VirtualFileUrl> by VirtualFileUrlListProperty()
  var excludedPatterns: List<String> by EntityDataDelegation()
  var module: ModuleEntity by MutableManyToOne.NotNull(ContentRootEntity::class.java, ModuleEntity::class.java)
  override fun getEntityClass(): Class<ContentRootEntity> {
    return ContentRootEntity::class.java
  }
}

class ModifiableLibraryEntity : ModifiableWorkspaceEntityBase<LibraryEntity>() {
  var tableId: LibraryTableId by EntityDataDelegation()
  var name: String by EntityDataDelegation()
  var roots: List<LibraryRoot> by VirtualFileUrlLibraryRootProperty()
  var excludedRoots: List<VirtualFileUrl> by VirtualFileUrlListProperty()
}

class ModifiableLibraryPropertiesEntity : ModifiableWorkspaceEntityBase<LibraryPropertiesEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.NotNull(LibraryPropertiesEntity::class.java, LibraryEntity::class.java)
  var libraryType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

class ModifiableSdkEntity : ModifiableWorkspaceEntityBase<SdkEntity>() {
  var library: LibraryEntity by MutableOneToOneChild.NotNull(SdkEntity::class.java, LibraryEntity::class.java)
  var homeUrl: VirtualFileUrl by VirtualFileUrlProperty()
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

class ModifiableFacetEntity : ModifiableWorkspaceEntityBase<FacetEntity>() {
  var name: String by EntityDataDelegation()
  var facetType: String by EntityDataDelegation()
  var configurationXmlTag: String? by EntityDataDelegation()
  var moduleId: ModuleId by EntityDataDelegation()

  var module: ModuleEntity by MutableManyToOne.NotNull(FacetEntity::class.java, ModuleEntity::class.java)
  var underlyingFacet: FacetEntity? by MutableManyToOne.Nullable(FacetEntity::class.java, FacetEntity::class.java)
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

class ModifiableArtifactPropertiesEntity : ModifiableWorkspaceEntityBase<ArtifactPropertiesEntity>() {
  var artifact: ArtifactEntity by MutableManyToOne.NotNull(ArtifactPropertiesEntity::class.java, ArtifactEntity::class.java)
  var providerType: String by EntityDataDelegation()
  var propertiesXmlTag: String? by EntityDataDelegation()
}

abstract class ModifiableCompositePackagingElementEntity<T: CompositePackagingElementEntity>(clazz: Class<T>) : ModifiableWorkspaceEntityBase<T>() {
  var children: Sequence<PackagingElementEntity> by MutableOneToAbstractMany(clazz, PackagingElementEntity::class.java)
}

class ModifiableArtifactRootElementEntity : ModifiableCompositePackagingElementEntity<ArtifactRootElementEntity>(
  ArtifactRootElementEntity::class.java
)

class ModifiableDirectoryPackagingElementEntity : ModifiableCompositePackagingElementEntity<DirectoryPackagingElementEntity>(
  DirectoryPackagingElementEntity::class.java) {
  var directoryName: String by EntityDataDelegation()
}

class ModifiableArchivePackagingElementEntity : ModifiableCompositePackagingElementEntity<ArchivePackagingElementEntity>(
  ArchivePackagingElementEntity::class.java) {
  var fileName: String by EntityDataDelegation()
}

class ModifiableArtifactOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ArtifactOutputPackagingElementEntity>() {
  var artifact: ArtifactId? by EntityDataDelegation()
}

class ModifiableModuleOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleOutputPackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

class ModifiableLibraryFilesPackagingElementEntity : ModifiableWorkspaceEntityBase<LibraryFilesPackagingElementEntity>() {
  var library: LibraryId? by EntityDataDelegation()
}

class ModifiableModuleSourcePackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleSourcePackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

class ModifiableModuleTestOutputPackagingElementEntity : ModifiableWorkspaceEntityBase<ModuleTestOutputPackagingElementEntity>() {
  var module: ModuleId? by EntityDataDelegation()
}

abstract class ModifiableFileOrDirectoryPackagingElement<T : FileOrDirectoryPackagingElementEntity> : ModifiableWorkspaceEntityBase<T>() {
  var filePath: VirtualFileUrl by VirtualFileUrlProperty()
}

class ModifiableDirectoryCopyPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<DirectoryCopyPackagingElementEntity>()

class ModifiableExtractedDirectoryPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<ExtractedDirectoryPackagingElementEntity>() {
  var pathInArchive: String by EntityDataDelegation()
}

class ModifiableFileCopyPackagingElementEntity : ModifiableFileOrDirectoryPackagingElement<FileCopyPackagingElementEntity>() {
  var renamedOutputFileName: String? by EntityDataDelegation()
}

class ModifiableCustomPackagingElementEntity : ModifiableCompositePackagingElementEntity<CustomPackagingElementEntity>(CustomPackagingElementEntity::class.java) {
  var typeId: String by EntityDataDelegation()
  var propertiesXmlTag: String by EntityDataDelegation()
}

