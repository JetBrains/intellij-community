// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.jetbrains.kotlin.idea.gradleTooling.KotlinFragmentResolvedBinaryDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinFragmentResolvedSourceDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinProjectModelImportingContext
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinFragmentReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinIdeFragmentDependencyReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinIdeLocalSourceFragmentDependencyReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinIdeMavenBinaryFragmentDependencyReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinFragmentResolvedDependency

internal object KotlinFragmentDependencyResolutionBuilder :
    KotlinProjectModelComponentBuilder<KotlinFragmentReflection, Collection<KotlinFragmentResolvedDependency>> {

    override fun buildComponent(
        origin: KotlinFragmentReflection, importingContext: KotlinProjectModelImportingContext
    ): Collection<KotlinFragmentResolvedDependency> {
        val dependencies = importingContext.fragmentDependencyResolver?.resolveDependencies(origin) ?: return emptyList()
        return dependencies.mapNotNull { it.toKotlinFragmentResolvedDependencyOrNull() }
    }

    private fun KotlinIdeFragmentDependencyReflection.toKotlinFragmentResolvedDependencyOrNull(): KotlinFragmentResolvedDependency? {
        return when (this) {
            is KotlinIdeLocalSourceFragmentDependencyReflection -> toKotlinFragmentResolvedSourceDependencyOrNull()
            is KotlinIdeMavenBinaryFragmentDependencyReflection -> toKotlinFragmentResolvedBinaryDependencyOrNull()
        }
    }

    // TODO: Handle composite builds (use buildId)
    private fun KotlinIdeLocalSourceFragmentDependencyReflection.toKotlinFragmentResolvedSourceDependencyOrNull():
            KotlinFragmentResolvedSourceDependency? {
        val projectPath = this.projectPath ?: return null
        val moduleName = this.kotlinModuleName ?: return null
        val fragmentName = this.kotlinFragmentName ?: return null
        val dependencyIdPrefix = projectPath.takeIf { it.isNotEmpty() && it != ":" } ?: projectName ?: return null
        return KotlinFragmentResolvedSourceDependency(
            "$dependencyIdPrefix:$fragmentName${moduleName.capitalize()}"
        )
    }

    private fun KotlinIdeMavenBinaryFragmentDependencyReflection.toKotlinFragmentResolvedBinaryDependencyOrNull():
            KotlinFragmentResolvedBinaryDependency? {
        val group = this.mavenGroup ?: return null
        val module = this.mavenModule ?: return null
        val version = this.version ?: return null

        val dependencyIdentifier = if (kotlinFragmentName == null) "$group:$module$version"
        else "$group:$module:$kotlinFragmentName:$version"

        return KotlinFragmentResolvedBinaryDependency(dependencyIdentifier, this.files?.toSet())
    }
}
