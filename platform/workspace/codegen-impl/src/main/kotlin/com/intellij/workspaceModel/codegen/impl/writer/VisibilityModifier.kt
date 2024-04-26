// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.writer

import com.intellij.workspaceModel.codegen.impl.engine.generatorSettings

private const val PUBLIC_MODIFIER = "public"

internal val generatedCodeVisibilityModifier: String
  get() = if (generatorSettings.explicitApiEnabled) PUBLIC_MODIFIER else ""