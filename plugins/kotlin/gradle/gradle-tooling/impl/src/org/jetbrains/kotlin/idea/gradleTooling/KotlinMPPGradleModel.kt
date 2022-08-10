// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.ModelFactory
import java.io.Serializable

typealias KotlinDependency = ExternalDependency

fun KotlinDependency.deepCopy(cache: MutableMap<Any, Any>): KotlinDependency {
    val cachedValue = cache[this] as? KotlinDependency
    return if (cachedValue != null) {
        cachedValue
    } else {
        val result = ModelFactory.createCopy(this)
        cache[this] = result
        result
    }
}

interface KotlinMPPGradleModel : KotlinSourceSetContainer, Serializable {
    val dependencyMap: Map<KotlinDependencyId, KotlinDependency>
    val targets: Collection<KotlinTarget>
    val extraFeatures: ExtraFeatures
    val kotlinNativeHome: String
    val cacheAware: CompilerArgumentsCacheAware

    @Deprecated(level = DeprecationLevel.WARNING, message = "Use KotlinMPPGradleModel#cacheAware instead")
    val partialCacheAware: CompilerArgumentsCacheAware
    val kotlinImportingDiagnostics: KotlinImportingDiagnosticsContainer

    @Deprecated("Use 'sourceSetsByName' instead", ReplaceWith("sourceSetsByName"), DeprecationLevel.ERROR)
    val sourceSets: Map<String, KotlinSourceSet>
        get() = sourceSetsByName

    override val sourceSetsByName: Map<String, KotlinSourceSet>

    companion object {
        const val NO_KOTLIN_NATIVE_HOME = ""
    }
}