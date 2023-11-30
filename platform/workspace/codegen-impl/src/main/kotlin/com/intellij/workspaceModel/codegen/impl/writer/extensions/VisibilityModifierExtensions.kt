// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer.extensions

import com.intellij.workspaceModel.codegen.deft.meta.ExtProperty
import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ObjModule

private const val PUBLIC_MODIFIER = "public"

internal val ObjClass<*>.generatedCodeVisibilityModifier: String
  get() = if (module.explicitApiEnabled) PUBLIC_MODIFIER else ""

internal val ExtProperty<*, *>.generatedCodeVisibilityModifier: String
  get() = if (module.explicitApiEnabled) PUBLIC_MODIFIER else ""

internal val ObjModule.generatedCodeVisibilityModifier: String
  get() = if (explicitApiEnabled) PUBLIC_MODIFIER else ""