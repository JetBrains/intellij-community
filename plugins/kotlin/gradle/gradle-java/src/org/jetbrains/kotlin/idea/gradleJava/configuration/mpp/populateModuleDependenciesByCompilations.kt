// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.fullName
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getKotlinModuleId

internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByCompilations(
    context: KotlinMppPopulateModuleDependenciesContext
): Unit = with(context) {
    getCompilations(gradleModule, mppModel, ideModule, resolverCtx)
        .filterNot { (_, compilation) -> shouldDelegateToOtherPlugin(compilation) }
        .filter { (_, compilation) -> processedModuleIds.add(getKotlinModuleId(gradleModule, compilation, resolverCtx)) }
        .forEach { (dataNode, compilation) ->
            buildDependencies(
                resolverCtx, sourceSetMap, artifactsMap, dataNode, getDependencies(compilation), ideProject
            )
            for (sourceSet in compilation.declaredSourceSets) {
                if (sourceSet.fullName() == compilation.fullName()) continue
                val targetDataNode = getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx) ?: continue
                addDependency(dataNode, targetDataNode, sourceSet.isTestModule)
            }
        }

}

