// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1.metaModel

import com.intellij.devkit.workspaceModel.metaModel.WorkspaceModelDefaults
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.name.FqName

internal fun Annotated.isAnnotatedBy(fqName: FqName) = annotations.hasAnnotation(fqName)

internal val FqName.isCollection: Boolean
  get() = isList || isSet || isMap

internal val FqName.isList: Boolean
  get() = this == WorkspaceModelDefaults.LIST_INTERFACE.fqName

internal val FqName.isSet: Boolean
  get() = this == WorkspaceModelDefaults.SET_INTERFACE.fqName

internal val FqName.isMap: Boolean
  get() = this == WorkspaceModelDefaults.MAP_INTERFACE.fqName

internal data class CompiledObjModuleAndK1Module(
  val compiledObjModule: CompiledObjModule,
  val kotlinModule: ModuleDescriptor,
)

internal infix fun CompiledObjModule.and(moduleDescriptor: ModuleDescriptor) = CompiledObjModuleAndK1Module(this, moduleDescriptor)
