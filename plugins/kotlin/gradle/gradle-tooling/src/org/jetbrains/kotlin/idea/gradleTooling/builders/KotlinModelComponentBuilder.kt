// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.MultiplatformModelImportingContext
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency

interface KotlinModelComponentBuilder<TCtx, TRet> {
    fun buildComponent(origin: Any, importingContext: TCtx): TRet?
}

interface KotlinModelComponentBuilderBase<TRet> : KotlinModelComponentBuilder<Unit, TRet> {
    override fun buildComponent(origin: Any, importingContext: Unit): TRet? = buildComponent(origin)

    fun buildComponent(origin: Any): TRet?
}

interface KotlinMultiplatformComponentBuilder<TRet> : KotlinModelComponentBuilder<MultiplatformModelImportingContext, TRet>

interface KotlinProjectModelComponentBuilder<TRet> : KotlinModelComponentBuilder<KotlinProjectModelImportingContext, TRet>

/**
 * Returns only those dependencies with RUNTIME scope which are not present with compile scope
 */
fun Collection<KotlinDependency>.onlyNewDependencies(compileDependencies: Collection<KotlinDependency>): List<KotlinDependency> {
    val compileDependencyArtefacts =
        compileDependencies.flatMap { (it as? ExternalProjectDependency)?.projectDependencyArtifacts ?: emptyList() }
    return this.filter {
        if (it is ExternalProjectDependency)
            !(compileDependencyArtefacts.containsAll(it.projectDependencyArtifacts))
        else
            true
    }
}
