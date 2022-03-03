// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.MultiplatformModelImportingContext
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency

interface KotlinModelComponentBuilder<TOrigin, TCtx, TRet> {
    fun buildComponent(origin: TOrigin, importingContext: TCtx): TRet?
}

interface KotlinModelComponentBuilderBase<TOrigin, TRet> : KotlinModelComponentBuilder<TOrigin, Unit, TRet> {
    override fun buildComponent(origin: TOrigin, importingContext: Unit): TRet? = buildComponent(origin)

    fun buildComponent(origin: TOrigin): TRet?
}

interface KotlinMultiplatformComponentBuilder<TOrigin, TRet> :
    KotlinModelComponentBuilder<TOrigin, MultiplatformModelImportingContext, TRet>

interface KotlinMultiplatformComponentBuilderBase<TRet> :
    KotlinModelComponentBuilder<Any, MultiplatformModelImportingContext, TRet>

interface KotlinProjectModelComponentBuilder<TOrigin, TRet> :
    KotlinModelComponentBuilder<TOrigin, KotlinProjectModelImportingContext, TRet>

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
