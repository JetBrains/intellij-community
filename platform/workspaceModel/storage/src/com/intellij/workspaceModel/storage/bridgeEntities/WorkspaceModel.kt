package org.jetbrains.workspaceModel

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArchivePackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactExternalSystemIdEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactExternalSystemIdEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactOutputPackagingElementEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactRootElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ArtifactsOrderEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.CustomPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.CustomSourceRootPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.DirectoryCopyPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.DirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.EclipseProjectPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.EclipseProjectPropertiesEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExtractedDirectoryPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetExternalSystemIdEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetExternalSystemIdEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetsOrderEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetsOrderEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.FileCopyPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.JavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.JavaResourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.JavaSourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryExternalSystemIdEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryExternalSystemIdEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryFilesPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryFilesPackagingElementEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.LibraryPropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleCustomImlDataEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntityImpl
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleGroupPathEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleSourcePackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleTestOutputPackagingElementEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.SdkEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootOrderEntity
import com.intellij.workspaceModel.storage.impl.ExtRefKey
import com.intellij.workspaceModel.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneChildOfParent
import com.intellij.workspaceModel.storage.impl.updateOneToOneParentOfChild
import com.intellij.workspaceModel.storage.referrersx
import org.jetbrains.deft.annotations.Child


var ArtifactOutputPackagingElementEntity.Builder.artifactEntity: ArtifactEntity
    get() {
        return referrersx(ArtifactEntity::artifactOutputPackagingElement).single()
    }
    set(value) {
        val diff = (this as ArtifactOutputPackagingElementEntityImpl.Builder).diff
        if (diff != null) {
            if ((value as ArtifactEntityImpl.Builder).diff == null) {
                value._artifactOutputPackagingElement = this
                diff.addEntity(value)
            }
            diff.updateOneToOneParentOfChild(ArtifactEntityImpl.ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("ArtifactEntity", "artifactOutputPackagingElement", false, ArtifactEntityImpl.ARTIFACTOUTPUTPACKAGINGELEMENT_CONNECTION_ID)
            this.extReferences[key] = value
            
            (value as ArtifactEntityImpl.Builder)._artifactOutputPackagingElement = this
        }
    }

var ArtifactEntity.Builder.artifactExternalSystemIdEntity: @Child ArtifactExternalSystemIdEntity?
    get() {
        return referrersx(ArtifactExternalSystemIdEntity::artifactEntity).singleOrNull()
    }
    set(value) {
        val diff = (this as ArtifactEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as ArtifactExternalSystemIdEntityImpl.Builder).diff == null) {
                    value._artifactEntity = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(ArtifactExternalSystemIdEntityImpl.ARTIFACTENTITY_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("ArtifactExternalSystemIdEntity", "artifactEntity", true, ArtifactExternalSystemIdEntityImpl.ARTIFACTENTITY_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as ArtifactExternalSystemIdEntityImpl.Builder)._artifactEntity = this
            }
        }
    }

var FacetEntity.Builder.childrenFacets: @Child List<FacetEntity>
    get() {
        return referrersx(FacetEntity::underlyingFacet)
    }
    set(value) {
        val diff = (this as FacetEntityImpl.Builder).diff
        if (diff != null) {
            for (item in value) {
                if ((item as FacetEntityImpl.Builder).diff == null) {
                    item._underlyingFacet = this
                    diff.addEntity(item)
                }
            }
            diff.updateOneToManyChildrenOfParent(FacetEntityImpl.UNDERLYINGFACET_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("FacetEntity", "underlyingFacet", true, FacetEntityImpl.UNDERLYINGFACET_CONNECTION_ID)
            this.extReferences[key] = value
            
            for (item in value) {
                (item as FacetEntityImpl.Builder)._underlyingFacet = this
            }
        }
    }

var ModuleEntity.Builder.eclipseProperties: @Child EclipseProjectPropertiesEntity?
    get() {
        return referrersx(EclipseProjectPropertiesEntity::module).singleOrNull()
    }
    set(value) {
        val diff = (this as ModuleEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as EclipseProjectPropertiesEntityImpl.Builder).diff == null) {
                    value._module = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(EclipseProjectPropertiesEntityImpl.MODULE_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("EclipseProjectPropertiesEntity", "module", true, EclipseProjectPropertiesEntityImpl.MODULE_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as EclipseProjectPropertiesEntityImpl.Builder)._module = this
            }
        }
    }

var LibraryEntity.Builder.externalSystemId: @Child LibraryExternalSystemIdEntity?
    get() {
        return referrersx(LibraryExternalSystemIdEntity::library).singleOrNull()
    }
    set(value) {
        val diff = (this as LibraryEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as LibraryExternalSystemIdEntityImpl.Builder).diff == null) {
                    value._library = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(LibraryExternalSystemIdEntityImpl.LIBRARY_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("LibraryExternalSystemIdEntity", "library", true, LibraryExternalSystemIdEntityImpl.LIBRARY_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as LibraryExternalSystemIdEntityImpl.Builder)._library = this
            }
        }
    }

var FacetEntity.Builder.facetExternalSystemIdEntity: @Child FacetExternalSystemIdEntity?
    get() {
        return referrersx(FacetExternalSystemIdEntity::facet).singleOrNull()
    }
    set(value) {
        val diff = (this as FacetEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as FacetExternalSystemIdEntityImpl.Builder).diff == null) {
                    value._facet = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(FacetExternalSystemIdEntityImpl.FACET_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("FacetExternalSystemIdEntity", "facet", true, FacetExternalSystemIdEntityImpl.FACET_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as FacetExternalSystemIdEntityImpl.Builder)._facet = this
            }
        }
    }

var ModuleEntity.Builder.facetOrder: @Child FacetsOrderEntity?
    get() {
        return referrersx(FacetsOrderEntity::moduleEntity).singleOrNull()
    }
    set(value) {
        val diff = (this as ModuleEntityImpl.Builder).diff
        if (diff != null) {
            if (value != null) {
                if ((value as FacetsOrderEntityImpl.Builder).diff == null) {
                    value._moduleEntity = this
                    diff.addEntity(value)
                }
            }
            diff.updateOneToOneChildOfParent(FacetsOrderEntityImpl.MODULEENTITY_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("FacetsOrderEntity", "moduleEntity", true, FacetsOrderEntityImpl.MODULEENTITY_CONNECTION_ID)
            this.extReferences[key] = value
            
            if (value != null) {
                (value as FacetsOrderEntityImpl.Builder)._moduleEntity = this
            }
        }
    }

var LibraryFilesPackagingElementEntity.Builder.libraryEntity: LibraryEntity
    get() {
        return referrersx(LibraryEntity::libraryFilesPackagingElement).single()
    }
    set(value) {
        val diff = (this as LibraryFilesPackagingElementEntityImpl.Builder).diff
        if (diff != null) {
            if ((value as LibraryEntityImpl.Builder).diff == null) {
                value._libraryFilesPackagingElement = this
                diff.addEntity(value)
            }
            diff.updateOneToOneParentOfChild(LibraryEntityImpl.LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID, this, value)
        }
        else {
            val key = ExtRefKey("LibraryEntity", "libraryFilesPackagingElement", false, LibraryEntityImpl.LIBRARYFILESPACKAGINGELEMENT_CONNECTION_ID)
            this.extReferences[key] = value
            
            (value as LibraryEntityImpl.Builder)._libraryFilesPackagingElement = this
        }
    }


fun MutableEntityStorage.modifyEntity(entity: ArchivePackagingElementEntity, modification: ArchivePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArchivePackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactEntity, modification: ArtifactEntity.Builder.() -> Unit) = modifyEntity(ArtifactEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactExternalSystemIdEntity, modification: ArtifactExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(ArtifactExternalSystemIdEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactOutputPackagingElementEntity, modification: ArtifactOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactPropertiesEntity, modification: ArtifactPropertiesEntity.Builder.() -> Unit) = modifyEntity(ArtifactPropertiesEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactRootElementEntity, modification: ArtifactRootElementEntity.Builder.() -> Unit) = modifyEntity(ArtifactRootElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ArtifactsOrderEntity, modification: ArtifactsOrderEntity.Builder.() -> Unit) = modifyEntity(ArtifactsOrderEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ContentRootEntity, modification: ContentRootEntity.Builder.() -> Unit) = modifyEntity(ContentRootEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: CustomPackagingElementEntity, modification: CustomPackagingElementEntity.Builder.() -> Unit) = modifyEntity(CustomPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: CustomSourceRootPropertiesEntity, modification: CustomSourceRootPropertiesEntity.Builder.() -> Unit) = modifyEntity(CustomSourceRootPropertiesEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: DirectoryCopyPackagingElementEntity, modification: DirectoryCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryCopyPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: DirectoryPackagingElementEntity, modification: DirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(DirectoryPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: EclipseProjectPropertiesEntity, modification: EclipseProjectPropertiesEntity.Builder.() -> Unit) = modifyEntity(EclipseProjectPropertiesEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ExternalSystemModuleOptionsEntity, modification: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) = modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ExtractedDirectoryPackagingElementEntity, modification: ExtractedDirectoryPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ExtractedDirectoryPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: FacetEntity, modification: FacetEntity.Builder.() -> Unit) = modifyEntity(FacetEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: FacetExternalSystemIdEntity, modification: FacetExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(FacetExternalSystemIdEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: FacetsOrderEntity, modification: FacetsOrderEntity.Builder.() -> Unit) = modifyEntity(FacetsOrderEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: FileCopyPackagingElementEntity, modification: FileCopyPackagingElementEntity.Builder.() -> Unit) = modifyEntity(FileCopyPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: JavaModuleSettingsEntity, modification: JavaModuleSettingsEntity.Builder.() -> Unit) = modifyEntity(JavaModuleSettingsEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: JavaResourceRootEntity, modification: JavaResourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaResourceRootEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: JavaSourceRootEntity, modification: JavaSourceRootEntity.Builder.() -> Unit) = modifyEntity(JavaSourceRootEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: LibraryEntity, modification: LibraryEntity.Builder.() -> Unit) = modifyEntity(LibraryEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: LibraryExternalSystemIdEntity, modification: LibraryExternalSystemIdEntity.Builder.() -> Unit) = modifyEntity(LibraryExternalSystemIdEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: LibraryFilesPackagingElementEntity, modification: LibraryFilesPackagingElementEntity.Builder.() -> Unit) = modifyEntity(LibraryFilesPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: LibraryPropertiesEntity, modification: LibraryPropertiesEntity.Builder.() -> Unit) = modifyEntity(LibraryPropertiesEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleCustomImlDataEntity, modification: ModuleCustomImlDataEntity.Builder.() -> Unit) = modifyEntity(ModuleCustomImlDataEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleEntity, modification: ModuleEntity.Builder.() -> Unit) = modifyEntity(ModuleEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleGroupPathEntity, modification: ModuleGroupPathEntity.Builder.() -> Unit) = modifyEntity(ModuleGroupPathEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleOutputPackagingElementEntity, modification: ModuleOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleSourcePackagingElementEntity, modification: ModuleSourcePackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleSourcePackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: ModuleTestOutputPackagingElementEntity, modification: ModuleTestOutputPackagingElementEntity.Builder.() -> Unit) = modifyEntity(ModuleTestOutputPackagingElementEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: SdkEntity, modification: SdkEntity.Builder.() -> Unit) = modifyEntity(SdkEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: SourceRootEntity, modification: SourceRootEntity.Builder.() -> Unit) = modifyEntity(SourceRootEntity.Builder::class.java, entity, modification)
fun MutableEntityStorage.modifyEntity(entity: SourceRootOrderEntity, modification: SourceRootOrderEntity.Builder.() -> Unit) = modifyEntity(SourceRootOrderEntity.Builder::class.java, entity, modification)
