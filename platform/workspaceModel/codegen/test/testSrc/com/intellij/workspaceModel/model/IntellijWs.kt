package org.jetbrains.deft.IntellijWs

import com.intellij.workspace.model.api.ArchivePackagingElementEntity
import com.intellij.workspace.model.api.ArchivePackagingElementEntityImpl
import com.intellij.workspace.model.api.ArtifactEntity
import com.intellij.workspace.model.api.ArtifactEntityImpl
import com.intellij.workspace.model.api.ArtifactOutputPackagingElementEntity
import com.intellij.workspace.model.api.ArtifactOutputPackagingElementEntityImpl
import com.intellij.workspace.model.api.ArtifactPropertiesEntity
import com.intellij.workspace.model.api.ArtifactPropertiesEntityImpl
import com.intellij.workspace.model.api.ArtifactRootElementEntity
import com.intellij.workspace.model.api.ArtifactRootElementEntityImpl
import com.intellij.workspace.model.api.CompositePackagingElementEntity
import com.intellij.workspace.model.api.ContentRootEntity
import com.intellij.workspace.model.api.ContentRootEntityImpl
import com.intellij.workspace.model.api.CustomPackagingElementEntity
import com.intellij.workspace.model.api.CustomPackagingElementEntityImpl
import com.intellij.workspace.model.api.CustomSourceRootPropertiesEntity
import com.intellij.workspace.model.api.CustomSourceRootPropertiesEntityImpl
import com.intellij.workspace.model.api.DirectoryCopyPackagingElementEntity
import com.intellij.workspace.model.api.DirectoryCopyPackagingElementEntityImpl
import com.intellij.workspace.model.api.DirectoryPackagingElementEntity
import com.intellij.workspace.model.api.DirectoryPackagingElementEntityImpl
import com.intellij.workspace.model.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspace.model.api.ExternalSystemModuleOptionsEntityImpl
import com.intellij.workspace.model.api.ExtractedDirectoryPackagingElementEntity
import com.intellij.workspace.model.api.ExtractedDirectoryPackagingElementEntityImpl
import com.intellij.workspace.model.api.FacetEntity
import com.intellij.workspace.model.api.FacetEntityImpl
import com.intellij.workspace.model.api.FileCopyPackagingElementEntity
import com.intellij.workspace.model.api.FileCopyPackagingElementEntityImpl
import com.intellij.workspace.model.api.FileOrDirectoryPackagingElementEntity
import com.intellij.workspace.model.api.FileOrDirectoryPackagingElementEntityImpl
import com.intellij.workspace.model.api.JavaModuleSettingsEntity
import com.intellij.workspace.model.api.JavaModuleSettingsEntityImpl
import com.intellij.workspace.model.api.JavaResourceRootEntity
import com.intellij.workspace.model.api.JavaResourceRootEntityImpl
import com.intellij.workspace.model.api.JavaSourceRootEntity
import com.intellij.workspace.model.api.JavaSourceRootEntityImpl
import com.intellij.workspace.model.api.LibraryEntity
import com.intellij.workspace.model.api.LibraryEntityImpl
import com.intellij.workspace.model.api.LibraryFilesPackagingElementEntity
import com.intellij.workspace.model.api.LibraryFilesPackagingElementEntityImpl
import com.intellij.workspace.model.api.LibraryPropertiesEntity
import com.intellij.workspace.model.api.LibraryPropertiesEntityImpl
import com.intellij.workspace.model.api.ModuleCustomImlDataEntity
import com.intellij.workspace.model.api.ModuleCustomImlDataEntityImpl
import com.intellij.workspace.model.api.ModuleEntity
import com.intellij.workspace.model.api.ModuleEntityImpl
import com.intellij.workspace.model.api.ModuleGroupPathEntity
import com.intellij.workspace.model.api.ModuleGroupPathEntityImpl
import com.intellij.workspace.model.api.ModuleOutputPackagingElementEntity
import com.intellij.workspace.model.api.ModuleOutputPackagingElementEntityImpl
import com.intellij.workspace.model.api.ModuleSourcePackagingElementEntity
import com.intellij.workspace.model.api.ModuleSourcePackagingElementEntityImpl
import com.intellij.workspace.model.api.ModuleTestOutputPackagingElementEntity
import com.intellij.workspace.model.api.ModuleTestOutputPackagingElementEntityImpl
import com.intellij.workspace.model.api.PackagingElementEntity
import com.intellij.workspace.model.api.SdkEntity
import com.intellij.workspace.model.api.SdkEntityImpl
import com.intellij.workspace.model.api.SourceRootEntity
import com.intellij.workspace.model.api.SourceRootEntityImpl
import com.intellij.workspace.model.api.SourceRootOrderEntity
import com.intellij.workspace.model.api.SourceRootOrderEntityImpl
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.deft.impl.ObjModule

import org.jetbrains.deft.impl.* 
                        
object IntellijWs: ObjModule(ObjModule.Id("org.jetbrains.deft.IntellijWs")) {
    @InitApi
    override fun init() {            
        
                    
        beginInit(48)
        add(ContentRootEntity)
        add(SourceRootEntity)
        add(SourceRootOrderEntity)
        add(CustomSourceRootPropertiesEntity)
        add(JavaSourceRootEntity)
        add(JavaResourceRootEntity)
        add(ArtifactEntity)
        add(ArtifactPropertiesEntity)
        add(PackagingElementEntity)
        add(CompositePackagingElementEntity)
        add(DirectoryPackagingElementEntity)
        add(ArchivePackagingElementEntity)
        add(ArtifactRootElementEntity)
        add(ArtifactOutputPackagingElementEntity)
        add(ModuleOutputPackagingElementEntity)
        add(LibraryFilesPackagingElementEntity)
        add(ModuleSourcePackagingElementEntity)
        add(ModuleTestOutputPackagingElementEntity)
        add(FileOrDirectoryPackagingElementEntity)
        add(DirectoryCopyPackagingElementEntity)
        add(ExtractedDirectoryPackagingElementEntity)
        add(FileCopyPackagingElementEntity)
        add(CustomPackagingElementEntity)
        add(FacetEntity)
        add(ModuleEntity)
        add(ModuleCustomImlDataEntity)
        add(ModuleGroupPathEntity)
        add(JavaModuleSettingsEntity)
        add(ExternalSystemModuleOptionsEntity)
        add(LibraryEntity)
        add(LibraryPropertiesEntity)
        add(SdkEntity)
    }
}



fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ContentRootEntity, modification: ContentRootEntity.Builder.() -> Unit) = modifyEntity(ContentRootEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceRootEntity, modification: SourceRootEntity.Builder.() -> Unit) = modifyEntity(SourceRootEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SourceRootOrderEntity, modification: SourceRootOrderEntity.Builder.() -> Unit) = modifyEntity(SourceRootOrderEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CustomSourceRootPropertiesEntity, modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(CustomSourceRootPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaSourceRootEntity, modification: JavaSourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaSourceRootEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaResourceRootEntity, modification: JavaResourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaResourceRootEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactEntity, modification: ArtifactEntity.Builder.() -> Unit) = modifyEntity(ArtifactEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactPropertiesEntity, modification: ArtifactPropertiesEntity.Builder.() -> Unit) = modifyEntity(ArtifactPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: DirectoryPackagingElementEntity, modification: DirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArchivePackagingElementEntity, modification: ArchivePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArchivePackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactRootElementEntity, modification: ArtifactRootElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactRootElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ArtifactOutputPackagingElementEntity, modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactOutputPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleOutputPackagingElementEntity, modification: ModuleOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleOutputPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryFilesPackagingElementEntity, modification: LibraryFilesPackagingElementEntity.Builder.() -> Unit) = modifyEntity(LibraryFilesPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleSourcePackagingElementEntity, modification: ModuleSourcePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleSourcePackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleTestOutputPackagingElementEntity, modification: ModuleTestOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleTestOutputPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FileOrDirectoryPackagingElementEntity, modification: FileOrDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(FileOrDirectoryPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: DirectoryCopyPackagingElementEntity, modification: DirectoryCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryCopyPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ExtractedDirectoryPackagingElementEntity, modification: ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ExtractedDirectoryPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FileCopyPackagingElementEntity, modification: FileCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(FileCopyPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: CustomPackagingElementEntity, modification: CustomPackagingElementEntity.Builder.() -> Unit) = modifyEntity(CustomPackagingElementEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: FacetEntity, modification: FacetEntity.Builder.() -> Unit) = modifyEntity(FacetEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleEntity, modification: ModuleEntity.Builder.() -> Unit) = modifyEntity(ModuleEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleCustomImlDataEntity, modification: ModuleCustomImlDataEntity.Builder.() -> Unit) = modifyEntity(ModuleCustomImlDataEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ModuleGroupPathEntity, modification: ModuleGroupPathEntity.Builder.() -> Unit) = modifyEntity(ModuleGroupPathEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: JavaModuleSettingsEntity, modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(JavaModuleSettingsEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: ExternalSystemModuleOptionsEntity, modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) = modifyEntity(ExternalSystemModuleOptionsEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryEntity, modification: LibraryEntity.Builder.() -> Unit) = modifyEntity(LibraryEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: LibraryPropertiesEntity, modification: LibraryPropertiesEntity.Builder.() -> Unit) = modifyEntity(LibraryPropertiesEntityImpl.Builder::class.java, entity, modification)
fun WorkspaceEntityStorageBuilder.modifyEntity(entity: SdkEntity, modification: SdkEntity.Builder.() -> Unit) = modifyEntity(SdkEntityImpl.Builder::class.java, entity, modification)
