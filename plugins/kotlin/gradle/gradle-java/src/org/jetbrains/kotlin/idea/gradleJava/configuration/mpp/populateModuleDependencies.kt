// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinUnresolvedBinaryDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinDependenciesContainer
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi

@OptIn(KotlinGradlePluginVersionDependentApi::class)
internal fun KotlinMppGradleProjectResolver.Context.populateModuleDependencies() {
    val dependenciesContainer = mppModel.dependencies
    if (dependenciesContainer != null) {
        populateModuleDependenciesWithDependenciesContainer(dependenciesContainer)
    } else {
        populateModuleDependenciesWithoutDependenciesContainer()
    }
}

/**
 *  New Kotlin Gradle Plugin versions will provide this dependencies container
 */
internal fun KotlinMppGradleProjectResolver.Context.populateModuleDependenciesWithDependenciesContainer(
    dependencies: IdeaKotlinDependenciesContainer
) {
    mppModel.dependencyMap.values.modifyDependenciesOnMppModules(projectDataNode)

    val extensionInstance = KotlinMppGradleProjectResolverExtension.buildInstance()

    mppModel.sourceSetsByName.values.forEach { sourceSet ->
        val sourceSetModuleId = KotlinSourceSetModuleId(resolverCtx, gradleModule, sourceSet)
        val sourceSetDataNode = projectDataNode.findSourceSetDataNode(sourceSetModuleId) ?: return@forEach
        val sourceSetDependencies = dependencies[sourceSet.name]

        /* Call into extension points, skipping dependency population of source set if instructed */
        if (extensionInstance.beforePopulateSourceSetDependencies(
                this, sourceSetDataNode, sourceSet, sourceSetDependencies
            ) == KotlinMppGradleProjectResolverExtension.Result.Skip
        ) return@forEach

        /*
         Support for non-mpp Android plugins, which are not aware of our new extension points yet
         This is explicitly placed *after* calling into the extension points, so they can still handle the
         request with the EP instead.
         */
        if (shouldDelegateToOtherPlugin(sourceSet)) return@forEach

        /*
        Some dependencies are represented as IdeaKotlinProjectArtifactDependency.
        Such dependencies can be substituted with the actual source sets that built this artifact.
         */
        val projectArtifactDependencyResolver = KotlinProjectArtifactDependencyResolver()
        val substitutedSourceSetDependencies = sourceSetDependencies.flatMap { dependency ->
            if (dependency is IdeaKotlinProjectArtifactDependency)
                projectArtifactDependencyResolver.resolve(this, sourceSetDataNode, dependency)
            else listOf(dependency)
        }.toSet()

        /* Add each substituted dependency, reporting build errors for unresolved dependencies */
        val createdDependencyNodes = substitutedSourceSetDependencies.flatMapIndexed { index, dependency ->
            if (dependency is IdeaKotlinUnresolvedBinaryDependency) {
                reportIdeaKotlinUnresolvedDependency(dependency, this, sourceSetModuleId)
            }
            sourceSetDataNode.addDependency(dependency)/* The classpath order of the dependencies is given by the order they were sent by the Kotlin Gradle Plugin */
                .onEach { it.data.setOrder(index) }
        }

        /* Calling into extensions, notifying them about all populated dependencies */
        extensionInstance.afterPopulateSourceSetDependencies(
            this, sourceSetDataNode, sourceSet, substitutedSourceSetDependencies, createdDependencyNodes
        )
    }
}

/**
 * Implementation for older Kotlin Gradle plugins that will use
 * IntelliJ injected code to resolve dependencies
 */
internal fun KotlinMppGradleProjectResolver.Context.populateModuleDependenciesWithoutDependenciesContainer() {
    val context = createKotlinMppPopulateModuleDependenciesContext(
        gradleModule = gradleModule, ideProject = projectDataNode, ideModule = moduleDataNode, resolverCtx = resolverCtx
    ) ?: return

    KotlinMppGradleProjectResolver.populateModuleDependenciesByCompilations(context)
    KotlinMppGradleProjectResolver.populateModuleDependenciesByPlatformPropagation(context)
    KotlinMppGradleProjectResolver.populateModuleDependenciesBySourceSetVisibilityGraph(context)
}
