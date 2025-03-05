// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.metaModel

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.annotations.Open
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds

enum class WorkspaceModelDefaults(val fqName: FqName, val classId: ClassId) {
  DEFAULT_ANNOTATION(FqName(Default::class.qualifiedName!!), ClassId.topLevel(FqName(Default::class.qualifiedName!!))),
  OPEN_ANNOTATION(FqName(Open::class.qualifiedName!!), ClassId.topLevel(FqName(Open::class.qualifiedName!!))),
  ABSTRACT_ANNOTATION(FqName(Abstract::class.qualifiedName!!), ClassId.topLevel(FqName(Abstract::class.qualifiedName!!))),
  CHILD_ANNOTATION(FqName(Child::class.qualifiedName!!), ClassId.topLevel(FqName(Child::class.qualifiedName!!))),
  EQUALS_BY_ANNOTATION(FqName(EqualsBy::class.qualifiedName!!), ClassId.topLevel(FqName(EqualsBy::class.qualifiedName!!))),

  LIST_INTERFACE(StandardNames.FqNames.list, StandardClassIds.List),
  SET_INTERFACE(StandardNames.FqNames.set, StandardClassIds.Set),
  MAP_INTERFACE(StandardNames.FqNames.map, StandardClassIds.Map),

  WORKSPACE_ENTITY(FqName(WorkspaceEntity::class.qualifiedName!!), ClassId.topLevel(FqName(WorkspaceEntity::class.qualifiedName!!))),
  ENTITY_SOURCE(FqName(EntitySource::class.qualifiedName!!), ClassId.topLevel(FqName(EntitySource::class.qualifiedName!!))),
  VIRTUAL_FILE_URL(FqName(VirtualFileUrl::class.qualifiedName!!), ClassId.topLevel(FqName(VirtualFileUrl::class.qualifiedName!!))),
  SYMBOLIC_ENTITY_ID(FqName(SymbolicEntityId::class.qualifiedName!!), ClassId.topLevel(FqName(SymbolicEntityId::class.qualifiedName!!))),
  ENTITY_POINTER(FqName(EntityPointer::class.qualifiedName!!), ClassId.topLevel(FqName(EntityPointer::class.qualifiedName!!)))
  ;
}
