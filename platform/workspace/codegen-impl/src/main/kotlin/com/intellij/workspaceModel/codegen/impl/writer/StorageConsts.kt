package com.intellij.workspaceModel.codegen.impl.writer

private const val workspaceBasePackageName = "com.intellij.platform.workspace"

private const val workspaceStoragePackageName = "$workspaceBasePackageName.storage"
private const val workspaceEntitiesPackageName = "$workspaceBasePackageName.jps.entities"
private const val workspaceStorageUrlPackageName = "$workspaceBasePackageName.storage.url"
private const val workspaceStorageImplPackageName = "$workspaceBasePackageName.storage.impl"
private const val workspaceStorageIndicesPackageName = "$workspaceBasePackageName.storage.impl.indices"
private const val workspaceStorageAnnotationsPackageName = "$workspaceBasePackageName.storage.annotations"
private const val workspaceStorageContainersPackageName = "$workspaceBasePackageName.storage.impl.containers"

private const val workspaceStorageMetadataPackageName = "$workspaceBasePackageName.storage.metadata"
private const val workspaceStorageMetamodelPackageName = "$workspaceStorageMetadataPackageName.model"
private const val workspaceStorageMetadataImplPackageName = "$workspaceStorageMetadataPackageName.impl"


internal val GeneratedCodeApiVersion = fqn(workspaceStoragePackageName, "GeneratedCodeApiVersion")
internal val GeneratedCodeImplVersion = fqn(workspaceStoragePackageName, "GeneratedCodeImplVersion")

internal object EntityStorage {
  private val packageName = workspaceStorageImplPackageName

  val extractOneToManyChildren = fqn(packageName, "extractOneToManyChildren")
  val extractOneToManyParent = fqn(packageName, "extractOneToManyParent")
  val extractOneToOneParent = fqn(packageName, "extractOneToOneParent")
  val extractOneToOneChild = fqn(packageName, "extractOneToOneChild")
  val extractOneToAbstractOneChild = fqn(packageName, "extractOneToAbstractOneChild")
  val extractOneToAbstractOneParent = fqn(packageName, "extractOneToAbstractOneParent")
  val extractOneToAbstractManyChildren = fqn(packageName, "extractOneToAbstractManyChildren")
  val extractOneToAbstractManyParent = fqn(packageName, "extractOneToAbstractManyParent")

  val updateOneToAbstractManyChildrenOfParent = fqn(packageName, "updateOneToAbstractManyChildrenOfParent")
  val updateOneToAbstractManyParentOfChild = fqn(packageName, "updateOneToAbstractManyParentOfChild")
  val updateOneToManyChildrenOfParent = fqn(packageName, "updateOneToManyChildrenOfParent")
  val updateOneToAbstractOneChildOfParent = fqn(packageName, "updateOneToAbstractOneChildOfParent")
  val updateOneToOneChildOfParent = fqn(packageName, "updateOneToOneChildOfParent")
  val updateOneToManyParentOfChild = fqn(packageName, "updateOneToManyParentOfChild")
  val updateOneToAbstractOneParentOfChild = fqn(packageName, "updateOneToAbstractOneParentOfChild")
  val updateOneToOneParentOfChild = fqn(packageName, "updateOneToOneParentOfChild")
  override fun toString(): String {
    return fqn(workspaceStoragePackageName, this::class.simpleName!!).toString()
  }
}
internal val MutableEntityStorage = fqn(workspaceStoragePackageName, "MutableEntityStorage")


internal val EntityType = fqn(workspaceStoragePackageName, "EntityType")
internal val ConnectionId = fqn(workspaceStorageImplPackageName, "ConnectionId")
internal val EntityLink = fqn(workspaceStorageImplPackageName, "EntityLink")
internal val WorkspaceEntityBase = fqn(workspaceStorageImplPackageName, "WorkspaceEntityBase")
internal val SoftLinkable = fqn(workspaceStorageImplPackageName, "SoftLinkable")

internal val VirtualFileUrl = fqn(workspaceStorageUrlPackageName, "VirtualFileUrl")
internal object WorkspaceEntity {
  private val className  = this::class.simpleName!!
  private val packageName = workspaceStoragePackageName
  private val fqn = fqn(packageName, className)

  val simpleName = fqn.simpleName
  val Builder = fqn(packageName, "$className.Builder")
  override fun toString(): String {
    return fqn.toString()
  }
}

internal val WorkspaceEntityData = fqn(workspaceStorageImplPackageName, "WorkspaceEntityData")


internal val EntitySource = fqn(workspaceStoragePackageName, "EntitySource")
internal val SymbolicEntityId = fqn(workspaceStoragePackageName, "SymbolicEntityId")
internal val WorkspaceEntityWithSymbolicId = fqn(workspaceStoragePackageName, "WorkspaceEntityWithSymbolicId")
internal val ModifiableWorkspaceEntityBase = fqn(workspaceStorageImplPackageName, "ModifiableWorkspaceEntityBase")

internal val WorkspaceMutableIndex = fqn(workspaceStorageIndicesPackageName, "WorkspaceMutableIndex")

internal object EntityInformation {
  private val className  = this::class.simpleName!!
  private val packageName = workspaceStoragePackageName

  val Serializer = fqn(packageName, "$className.Serializer")
  val Deserializer = fqn(packageName, "$className.Deserializer")
  override fun toString(): String {
    return fqn(packageName, className).toString()
  }
}

//Storage metamodel classes
internal object MetadataStorage {
  private const val BASE_NAME = "MetadataStorageBase"
  internal const val IMPL_NAME = "MetadataStorageImpl"

  internal val base = fqn(workspaceStorageMetadataImplPackageName, BASE_NAME)

  internal val addMetadata = "addMetadata"
  internal val getMetadataByTypeFqn = "getMetadataByTypeFqn"
}

internal val EntityMetadata = fqn(workspaceStorageMetamodelPackageName, "EntityMetadata")

internal val StorageTypeMetadata = fqn(workspaceStorageMetamodelPackageName, "StorageTypeMetadata")
internal val ClassMetadata = fqn(workspaceStorageMetamodelPackageName, "FinalClassMetadata.ClassMetadata")
internal val ObjectMetadata = fqn(workspaceStorageMetamodelPackageName, "FinalClassMetadata.ObjectMetadata")
internal val EnumClassMetadata = fqn(workspaceStorageMetamodelPackageName, "FinalClassMetadata.EnumClassMetadata")
internal val KnownClass = fqn(workspaceStorageMetamodelPackageName, "FinalClassMetadata.KnownClass")
internal val AbstractClassMetadata = fqn(workspaceStorageMetamodelPackageName, "ExtendableClassMetadata.AbstractClassMetadata")

internal val OwnPropertyMetadata = fqn(workspaceStorageMetamodelPackageName, "OwnPropertyMetadata")
internal val ExtPropertyMetadata = fqn(workspaceStorageMetamodelPackageName, "ExtPropertyMetadata")

internal val ParameterizedType = fqn(workspaceStorageMetamodelPackageName, "ValueTypeMetadata.ParameterizedType")
internal val PrimitiveType = fqn(workspaceStorageMetamodelPackageName, "ValueTypeMetadata.SimpleType.PrimitiveType")
internal val CustomType = fqn(workspaceStorageMetamodelPackageName, "ValueTypeMetadata.SimpleType.CustomType")
internal val EntityReference = fqn(workspaceStorageMetamodelPackageName, "ValueTypeMetadata.EntityReference")

// Entity
internal val LibraryEntity = fqn(workspaceEntitiesPackageName, "LibraryEntity")
internal val LibraryRoot = fqn(workspaceEntitiesPackageName, "LibraryRoot")
internal val SdkEntity = fqn(workspaceEntitiesPackageName, "SdkEntity")
internal val SdkRoot = fqn(workspaceEntitiesPackageName, "SdkRoot")


// Annotations
internal val Child = fqn(workspaceStorageAnnotationsPackageName, "Child")


// Collections
internal val MutableWorkspaceSet = fqn(workspaceStorageContainersPackageName, "MutableWorkspaceSet")
internal val MutableWorkspaceList = fqn(workspaceStorageContainersPackageName, "MutableWorkspaceList")

internal object StorageCollection {
  private val packageName = workspaceStorageContainersPackageName

  val toMutableWorkspaceSet = fqn(packageName, "toMutableWorkspaceSet")
  val toMutableWorkspaceList = fqn(packageName, "toMutableWorkspaceList")
}
