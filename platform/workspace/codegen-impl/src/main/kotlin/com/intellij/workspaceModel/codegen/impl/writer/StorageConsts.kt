package com.intellij.workspaceModel.codegen.impl.writer

internal val GeneratedCodeApiVersion = fqn("com.intellij.platform.workspace.storage", "GeneratedCodeApiVersion")
internal val GeneratedCodeImplVersion = fqn("com.intellij.platform.workspace.storage", "GeneratedCodeImplVersion")

internal object EntityStorage {
  private val className  = this::class.simpleName!!
  private val packageName = "com.intellij.platform.workspace.storage"

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
    return fqn(packageName, className).toString()
  }
}
internal val MutableEntityStorage = fqn("com.intellij.platform.workspace.storage", "MutableEntityStorage")


internal val EntityType = fqn("com.intellij.platform.workspace.storage", "EntityType")
internal val ConnectionId = fqn("com.intellij.platform.workspace.storage.impl", "ConnectionId")
internal val EntityLink = fqn("com.intellij.platform.workspace.storage.impl", "EntityLink")
internal val WorkspaceEntityBase = fqn("com.intellij.platform.workspace.storage.impl", "WorkspaceEntityBase")
internal val SoftLinkable = fqn("com.intellij.platform.workspace.storage.impl", "SoftLinkable")

internal val VirtualFileUrl = fqn("com.intellij.platform.workspace.storage.url", "VirtualFileUrl.decoded")
internal object WorkspaceEntity {
  private val className  = this::class.simpleName!!
  private val packageName = "com.intellij.platform.workspace.storage"
  private val fqn = fqn(packageName, className)

  val simpleName = fqn.simpleName
  val Builder = fqn(packageName, "$className.Builder")
  override fun toString(): String {
    return fqn.toString()
  }
}

internal val WorkspaceEntityData = fqn("com.intellij.platform.workspace.storage.impl", "WorkspaceEntityData")
internal val UsedClassesCollector = fqn("com.intellij.platform.workspace.storage.impl", "UsedClassesCollector")


internal val EntitySource = fqn("com.intellij.platform.workspace.storage", "EntitySource")
internal val SymbolicEntityId = fqn("com.intellij.platform.workspace.storage", "SymbolicEntityId")
internal val WorkspaceEntityWithSymbolicId = fqn("com.intellij.platform.workspace.storage", "WorkspaceEntityWithSymbolicId")
internal val ModifiableWorkspaceEntityBase = fqn("com.intellij.platform.workspace.storage.impl", "ModifiableWorkspaceEntityBase")

internal val WorkspaceMutableIndex = fqn("com.intellij.platform.workspace.storage.impl.indices", "WorkspaceMutableIndex")

internal object EntityInformation {
  private val className  = this::class.simpleName!!
  private val packageName = "com.intellij.platform.workspace.storage"

  val Serializer = fqn(packageName, "$className.Serializer")
  val Deserializer = fqn(packageName, "$className.Deserializer")
  override fun toString(): String {
    return fqn(packageName, className).toString()
  }
}

// Entity
internal val LibraryEntity = fqn("com.intellij.platform.workspace.jps.entities", "LibraryEntity")
internal val LibraryRoot = fqn("com.intellij.platform.workspace.jps.entities", "LibraryRoot")

// Annotations
internal val Child = fqn("com.intellij.platform.workspace.storage.annotations", "Child")

// Collections
internal val MutableWorkspaceSet = fqn("com.intellij.platform.workspace.storage.impl.containers", "MutableWorkspaceSet")
internal val MutableWorkspaceList = fqn("com.intellij.platform.workspace.storage.impl.containers", "MutableWorkspaceList")

internal object StorageCollection {
  private val packageName = "com.intellij.platform.workspace.storage.impl.containers"

  val toMutableWorkspaceSet = fqn(packageName, "toMutableWorkspaceSet")
  val toMutableWorkspaceList = fqn(packageName, "toMutableWorkspaceList")
}
