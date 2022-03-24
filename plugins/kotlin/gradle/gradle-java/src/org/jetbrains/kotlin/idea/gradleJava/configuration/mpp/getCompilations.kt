// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun KotlinMPPGradleProjectResolver.Companion.getCompilations(
    gradleModule: IdeaModule,
    mppModel: KotlinMPPGradleModel,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext
): Sequence<Pair<DataNode<GradleSourceSetData>, KotlinCompilation>> {

    val sourceSetsMap = HashMap<String, DataNode<GradleSourceSetData>>()
    for (dataNode in ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {
        if (dataNode.kotlinSourceSetData?.sourceSetInfo != null) {
            sourceSetsMap[dataNode.data.id] = dataNode
        }
    }

    return sequence {
        for (target in mppModel.targets) {
            for (compilation in target.compilations) {
                val moduleId = getKotlinModuleId(gradleModule, compilation, resolverCtx)
                val moduleDataNode = sourceSetsMap[moduleId] ?: continue
                yield(moduleDataNode to compilation)
            }
        }
    }
}


