// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.k2.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceInheritanceInspectionBaseTest
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class WorkspaceInheritanceInspectionTest : WorkspaceInheritanceInspectionBaseTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}