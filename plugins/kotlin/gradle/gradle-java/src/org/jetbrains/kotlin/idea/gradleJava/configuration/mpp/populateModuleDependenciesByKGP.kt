// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.project.*
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi

@OptIn(KotlinGradlePluginVersionDependentApi::class)
internal fun KotlinMppPopulateModuleDependenciesContext.populateModuleDependenciesByKGP() {
    val dependencies = mppModel.dependencies ?: return
    mppModel.sourceSetsByName.values.forEach { sourceSet ->
        val sourceSetModuleIde = KotlinSourceSetModuleId(resolverCtx, gradleIdeaModule, sourceSet)
        val sourceSetDataNode = ideModule.findSourceSetNode(sourceSetModuleIde) ?: return@forEach
        dependencies[sourceSet.name].forEachIndexed { index, dependency ->
            sourceSetDataNode.addDependency(dependency)?.data?.setOrder(index)
        }
    }
}
