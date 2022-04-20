package org.jetbrains.deft.codegen.ijws

import org.jetbrains.deft.codegen.utils.fqn

fun wsFqn(name: String): String {
  var packageName = when (name) {
    "SoftLinkable",
    "ConnectionId",
    "WorkspaceEntityData",
    "WorkspaceEntityBase",
    "ModifiableWorkspaceEntityBase" -> "com.intellij.workspaceModel.storage.impl"
    "EntitySource",
    "WorkspaceEntity",
    "EntityReference",
    "PersistentEntityId",
    "WorkspaceEntityStorage",
    "ModifiableWorkspaceEntity",
    "WorkspaceEntityStorageBuilder" -> "com.intellij.workspaceModel.storage"
    "VirtualFileUrl" -> "com.intellij.workspaceModel.storage.url"
    "WorkspaceMutableIndex" -> "com.intellij.workspaceModel.storage.impl.indices"
    "KMutableProperty1" -> "kotlin.reflect"
    "memberProperties" -> "kotlin.reflect.full"
    "ExtRefKey" -> "com.intellij.workspaceModel.storage.impl"
    "Child" -> "org.jetbrains.deft.annotations"
    "referrersx" -> "com.intellij.workspaceModel.storage"
    "referrersy" -> "com.intellij.workspaceModel.storage"
    else -> null

  }
  if (name.startsWith("extractOneTo") ||
      name.startsWith("updateOneTo")) packageName = "com.intellij.workspaceModel.storage.impl"

  return fqn(packageName, name)
}