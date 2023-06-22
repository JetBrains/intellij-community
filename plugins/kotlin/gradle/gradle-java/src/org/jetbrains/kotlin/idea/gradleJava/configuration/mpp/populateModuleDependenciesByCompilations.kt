// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.fullName
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId

internal fun KotlinMppGradleProjectResolver.Companion.populateModuleDependenciesByCompilations(
    context: KotlinMppPopulateModuleDependenciesContext
): Unit = with(context) {
    getCompilations(gradleIdeaModule, mppModel, ideModule, resolverCtx)
        .filterNot { (_, compilation) -> shouldDelegateToOtherPlugin(compilation) }
        .filter { (_, compilation) -> processedModuleIds.add(getKotlinModuleId(gradleIdeaModule, compilation, resolverCtx)) }
        .forEach { (dataNode, compilation) ->
            buildDependencies(
                resolverCtx, sourceSetMap, artifactsMap, dataNode, getDependencies(compilation), ideProject
            )
            for (sourceSet in compilation.declaredSourceSets) {
                if (sourceSet.fullName() == compilation.fullName()) continue
                val targetDataNode = getSiblingKotlinModuleData(sourceSet, gradleIdeaModule, ideModule, resolverCtx) ?: continue
                addDependency(dataNode, targetDataNode, sourceSet.isTestComponent)
            }
        }

}

