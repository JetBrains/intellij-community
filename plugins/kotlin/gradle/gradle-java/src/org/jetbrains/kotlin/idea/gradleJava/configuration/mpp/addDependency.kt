// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver

internal fun KotlinMppGradleProjectResolver.Companion.addDependency(
    fromModule: DataNode<*>, toModule: DataNode<*>, dependOnTestModule: Boolean
) {
    if (fromModule.data == toModule.data) return
    val fromData = fromModule.data as? ModuleData ?: return
    val toData = toModule.data as? ModuleData ?: return

    val existing = fromModule.children.mapNotNull { it.data as? ModuleDependencyData }
        .filter { it.target.id == (toModule.data as? ModuleData)?.id }

    val nodeToModify = existing.singleOrNull() ?: existing.firstOrNull { it.scope == DependencyScope.COMPILE } ?: existing.firstOrNull()
    if (nodeToModify != null) {
        nodeToModify.scope = DependencyScope.COMPILE
        nodeToModify.isProductionOnTestDependency = nodeToModify.isProductionOnTestDependency || dependOnTestModule
        return
    }

    val moduleDependencyData = ModuleDependencyData(fromData, toData).also {
        it.scope = DependencyScope.COMPILE
        it.isExported = false
        it.isProductionOnTestDependency = dependOnTestModule
    }

    fromModule.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
}
