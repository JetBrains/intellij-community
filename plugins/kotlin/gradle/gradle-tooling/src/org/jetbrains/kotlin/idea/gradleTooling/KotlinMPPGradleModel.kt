// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:Suppress("DeprecatedCallableAddReplaceWith")

package org.jetbrains.kotlin.idea.gradleTooling

import org.jetbrains.kotlin.idea.gradleTooling.arguments.AbstractCompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.ModelFactory
import java.io.Serializable

typealias KotlinDependency = ExternalDependency

class KotlinDependencyMapper {
    private var currentIndex: KotlinDependencyId = 0
    private val idToDependency = HashMap<KotlinDependencyId, KotlinDependency>()
    private val dependencyToId = HashMap<KotlinDependency, KotlinDependencyId>()

    fun getDependency(id: KotlinDependencyId) = idToDependency[id]

    fun getId(dependency: KotlinDependency): KotlinDependencyId {
        return dependencyToId[dependency] ?: let {
            currentIndex++
            dependencyToId[dependency] = currentIndex
            idToDependency[currentIndex] = dependency
            return currentIndex
        }
    }

    fun toDependencyMap(): Map<KotlinDependencyId, KotlinDependency> = idToDependency
}

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