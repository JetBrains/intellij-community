// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndexTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants

abstract class GradleModuleDataIndexTestCase : ExternalSystemModuleDataIndexTestCase() {

  fun assertModuleNode(expectedModuleName: String, expectedProjectPath: String, actualModuleNode: DataNode<out ModuleData>?) =
    assertModuleNode(GradleConstants.SYSTEM_ID, expectedModuleName, expectedProjectPath, actualModuleNode)
}