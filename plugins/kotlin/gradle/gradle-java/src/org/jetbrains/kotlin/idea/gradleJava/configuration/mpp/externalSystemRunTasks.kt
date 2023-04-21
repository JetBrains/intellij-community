// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinAndroidSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun populateExternalSystemRunTasks(
    gradleModule: IdeaModule,
    mainModuleNode: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext
) {
    val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
    val sourceSetToRunTasks = KotlinModuleUtils.calculateRunTasks(mppModel, gradleModule, resolverCtx)
    val allKotlinSourceSets =
        ExternalSystemApiUtil.findAllRecursively(mainModuleNode, KotlinSourceSetData.KEY).mapNotNull { it?.data?.sourceSetInfo } +
                ExternalSystemApiUtil.find(mainModuleNode, KotlinAndroidSourceSetData.KEY)?.data?.sourceSetInfos.orEmpty()

    val allKotlinSourceSetsDataWithRunTasks = allKotlinSourceSets
        .associateWith {
            when (val component = it.kotlinComponent) {
                is KotlinCompilation -> component
                    .declaredSourceSets
                    .firstNotNullOfOrNull { sourceSetToRunTasks[it] }
                    .orEmpty()

                is KotlinSourceSet -> sourceSetToRunTasks[component]
                    .orEmpty()
                else -> error("Unsupported KotlinComponent: $component")
            }
        }
    allKotlinSourceSetsDataWithRunTasks.forEach { (sourceSetInfo, runTasks) -> sourceSetInfo.externalSystemRunTasks = runTasks }
}
