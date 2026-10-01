// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

private const val workspaceBasePackageName = "com.intellij.platform.workspace"

private const val workspaceStoragePackageName = "$workspaceBasePackageName.storage"
private const val workspaceEntitiesPackageName = "$workspaceBasePackageName.jps.entities"
private const val workspaceStorageUrlPackageName = "$workspaceBasePackageName.storage.url"
private const val workspaceStorageImplPackageName = "$workspaceBasePackageName.storage.impl"
private const val workspaceStorageIndicesPackageName = "$workspaceBasePackageName.storage.impl.indices"
private const val workspaceStorageAnnotationsPackageName = "$workspaceBasePackageName.storage.annotations"
private const val workspaceStorageContainersPackageName = "$workspaceBasePackageName.storage.impl.containers"
private const val workspaceStorageInstrumentationPackageName = "$workspaceBasePackageName.storage.instrumentation"

private const val workspaceStorageMetadataPackageName = "$workspaceBasePackageName.storage.metadata"
private const val workspaceStorageMetamodelPackageName = "$workspaceStorageMetadataPackageName.model"
private const val workspaceStorageMetadataImplPackageName = "$workspaceStorageMetadataPackageName.impl"


internal val GeneratedCodeApiVersion = fqn(workspaceStoragePackageName, "GeneratedCodeApiVersion")
internal val GeneratedCodeImplVersion = fqn(workspaceStoragePackageName, "GeneratedCodeImplVersion")

internal val MutableEntityStorage = fqn(workspaceStoragePackageName, "MutableEntityStorage")
internal val EntityStorageInstrumentationApi = fqn(workspaceStorageInstrumentationPackageName, "EntityStorageInstrumentationApi")
internal val WorkspaceEntityInternalApi = fqn(workspaceStoragePackageName, "WorkspaceEntityInternalApi")

internal val EntityType = fqn(workspaceStoragePackageName, "EntityType")
internal val ConnectionId = fqn(workspaceStoragePackageName, "ConnectionId")
internal val WorkspaceEntityBase = fqn(workspaceStorageImplPackageName, "WorkspaceEntityBase")
internal val SoftLinkable = fqn(workspaceStorageImplPackageName, "SoftLinkable")

internal val VirtualFileUrl = fqn(workspaceStorageUrlPackageName, "VirtualFileUrl")
internal object WorkspaceEntity {
  private const val packageName = workspaceStoragePackageName
  private val className  = this::class.simpleName!!
  private val fqn = fqn(packageName, className)

  val simpleName = fqn.simpleName
  val Builder = fqn(packageName, "${className}Builder")
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

//Storage metamodel classes
internal object MetadataStorage {
  private const val BASE_NAME = "MetadataStorageBase"
  private const val BRIDGE_NAME = "MetadataStorageBridge"
  internal const val IMPL_NAME = "MetadataStorageImpl"

  internal val base = fqn(workspaceStorageMetadataImplPackageName, BASE_NAME)
  internal val bridge = fqn(workspaceStorageMetadataPackageName, BRIDGE_NAME)

  internal const val addMetadata = "addMetadata"
  internal const val addMetadataHash = "addMetadataHash"
  internal const val getMetadataByTypeFqn = "getMetadataByTypeFqn"

  override fun toString(): String {
    return fqn(workspaceStorageMetadataPackageName, "MetadataStorage").toString()
  }
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

// Annotations
internal val Parent = fqn(workspaceStorageAnnotationsPackageName, "Parent")
internal val EqualsBy = fqn(workspaceStoragePackageName, "EqualsBy")
internal val ToString = fqn(workspaceStorageAnnotationsPackageName, "ToString")
internal val Internal = fqn("org.jetbrains.annotations.ApiStatus", "Internal")
internal val K1Deprecation = fqn("org.jetbrains.kotlin", "K1Deprecation")
internal val IndexVfu = fqn(workspaceStorageAnnotationsPackageName, "IndexVfu")


// Collections
internal val MutableWorkspaceSet = fqn(workspaceStorageContainersPackageName, "MutableWorkspaceSet")
internal val MutableWorkspaceList = fqn(workspaceStorageContainersPackageName, "MutableWorkspaceList")

internal object StorageCollection {
  private const val packageName = workspaceStorageContainersPackageName

  val toMutableWorkspaceSet = fqn(packageName, "toMutableWorkspaceSet")
  val toMutableWorkspaceList = fqn(packageName, "toMutableWorkspaceList")
}

internal const val symbolicIdPropertyName = "symbolicId"
internal const val entitySourcePropertyName = "entitySource"

internal object WorkspaceEntityBaseMembers {
  internal const val connectionIdListFun = "connectionIdList"
  internal const val calculateSymbolicIdFun = "calculateSymbolicId"
  internal const val getParentFun = "getParent"
  internal const val getChildFun = "getChild"
  internal const val getChildrenFun = "getChildren"
  internal const val addChangedPropertyFun = "addChangedProperty"
  internal const val outOfDiffOrModifiableFun = "outOfDiffOrModifiable"
  internal const val inDiffFun = "inDiff"
}
