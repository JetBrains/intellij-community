// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.projectModel.*
import java.io.Serializable

interface KotlinMPPGradleModel : KotlinSourceSetContainer, Serializable {
    val dependencyMap: Map<KotlinDependencyId, KotlinDependency>
    val targets: Collection<KotlinTarget>
    val extraFeatures: ExtraFeatures
    val kotlinNativeHome: String
    val cacheAware: CompilerArgumentsCacheAware

    @Deprecated(level = DeprecationLevel.WARNING, message = "Use KotlinMPPGradleModel#cacheAware instead")
    val partialCacheAware: CompilerArgumentsCacheAware

    @Deprecated("Use 'sourceSetsByName' instead", ReplaceWith("sourceSetsByName"), DeprecationLevel.ERROR)
    val sourceSets: Map<String, KotlinSourceSet>
        get() = sourceSetsByName

    override val sourceSetsByName: Map<String, KotlinSourceSet>

    val kotlinImportingDiagnostics: KotlinImportingDiagnosticsContainer
    val kotlinGradlePluginVersion: KotlinGradlePluginVersion?

    companion object {
        const val NO_KOTLIN_NATIVE_HOME = ""
    }
}

