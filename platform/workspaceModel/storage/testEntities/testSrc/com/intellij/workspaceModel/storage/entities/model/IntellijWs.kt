package org.jetbrains.deft.IntellijWs

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.entities.model.api.ArchivePackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ArtifactEntity
import com.intellij.workspaceModel.storage.entities.model.api.ArtifactOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ArtifactPropertiesEntity
import com.intellij.workspaceModel.storage.entities.model.api.ArtifactRootElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ContentRootEntity
import com.intellij.workspaceModel.storage.entities.model.api.CustomPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.CustomSourceRootPropertiesEntity
import com.intellij.workspaceModel.storage.entities.model.api.DirectoryCopyPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.DirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.entities.model.api.ExtractedDirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.FacetEntity
import com.intellij.workspaceModel.storage.entities.model.api.FileCopyPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.FileOrDirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.JavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.entities.model.api.JavaResourceRootEntity
import com.intellij.workspaceModel.storage.entities.model.api.JavaSourceRootEntity
import com.intellij.workspaceModel.storage.entities.model.api.LibraryEntity
import com.intellij.workspaceModel.storage.entities.model.api.LibraryFilesPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.LibraryPropertiesEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleCustomImlDataEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleGroupPathEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleSourcePackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.ModuleTestOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.entities.model.api.SdkEntity
import com.intellij.workspaceModel.storage.entities.model.api.SourceRootEntity
import com.intellij.workspaceModel.storage.entities.model.api.SourceRootOrderEntity

import org.jetbrains.deft.impl.* 



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ContentRootEntity, modification: ContentRootEntity.Builder.() -> Unit) = modifyEntity(ContentRootEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceRootEntity, modification: SourceRootEntity.Builder.() -> Unit) = modifyEntity(SourceRootEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceRootOrderEntity, modification: SourceRootOrderEntity.Builder.() -> Unit) = modifyEntity(SourceRootOrderEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CustomSourceRootPropertiesEntity, modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(CustomSourceRootPropertiesEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaSourceRootEntity, modification: JavaSourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaSourceRootEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaResourceRootEntity, modification: JavaResourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaResourceRootEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactEntity, modification: ArtifactEntity.Builder.() -> Unit) = modifyEntity(ArtifactEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactPropertiesEntity, modification: ArtifactPropertiesEntity.Builder.() -> Unit) = modifyEntity(ArtifactPropertiesEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: DirectoryPackagingElementEntity, modification: DirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArchivePackagingElementEntity, modification: ArchivePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArchivePackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactRootElementEntity, modification: ArtifactRootElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactRootElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactOutputPackagingElementEntity, modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleOutputPackagingElementEntity, modification: ModuleOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryFilesPackagingElementEntity, modification: LibraryFilesPackagingElementEntity.Builder.() -> Unit) = modifyEntity(LibraryFilesPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleSourcePackagingElementEntity, modification: ModuleSourcePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleSourcePackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleTestOutputPackagingElementEntity, modification: ModuleTestOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleTestOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FileOrDirectoryPackagingElementEntity, modification: FileOrDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(FileOrDirectoryPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: DirectoryCopyPackagingElementEntity, modification: DirectoryCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryCopyPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ExtractedDirectoryPackagingElementEntity, modification: ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ExtractedDirectoryPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FileCopyPackagingElementEntity, modification: FileCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CustomPackagingElementEntity, modification: CustomPackagingElementEntity.Builder.() -> Unit) = modifyEntity(CustomPackagingElementEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FacetEntity, modification: FacetEntity.Builder.() -> Unit) = modifyEntity(FacetEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleEntity, modification: ModuleEntity.Builder.() -> Unit) = modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleCustomImlDataEntity, modification: ModuleCustomImlDataEntity.Builder.() -> Unit) = modifyEntity(ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleGroupPathEntity, modification: ModuleGroupPathEntity.Builder.() -> Unit) = modifyEntity(ModuleGroupPathEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaModuleSettingsEntity, modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(JavaModuleSettingsEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ExternalSystemModuleOptionsEntity, modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) = modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryEntity, modification: LibraryEntity.Builder.() -> Unit) = modifyEntity(LibraryEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryPropertiesEntity, modification: LibraryPropertiesEntity.Builder.() -> Unit) = modifyEntity(LibraryPropertiesEntity.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SdkEntity, modification: SdkEntity.Builder.() -> Unit) = modifyEntity(SdkEntity.Builder::class.java, entity, modification)
