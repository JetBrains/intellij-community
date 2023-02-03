// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinDependenciesContainer
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@OptIn(KotlinGradlePluginVersionDependentApi::class)
internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependencies(
    gradleModule: IdeaModule,
    ideProject: DataNode<ProjectData>,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext,
    mppModel: KotlinMPPGradleModel,
) {
    val dependenciesContainer = mppModel.dependencies

    if (dependenciesContainer != null) {
        populateModuleDependenciesWithDependenciesContainer(
            gradleModule, ideProject, ideModule, resolverCtx, mppModel, dependenciesContainer
        )
    } else {
        populateModuleDependenciesWithoutDependenciesContainer(gradleModule, ideProject, ideModule, resolverCtx)
    }
}

/**
 *  New Kotlin Gradle Plugin versions will provide this dependencies container
 */
internal fun populateModuleDependenciesWithDependenciesContainer(
    gradleModule: IdeaModule,
    ideProject: DataNode<ProjectData>,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext,
    mppModel: KotlinMPPGradleModel,
    dependencies: IdeaKotlinDependenciesContainer
) {
    mppModel.dependencyMap.values.modifyDependenciesOnMppModules(ideProject)

    val extensionContext = KotlinMppGradleProjectResolverExtension.Context(mppModel, resolverCtx, gradleModule, ideModule)
    val extensionInstance = KotlinMppGradleProjectResolverExtension.buildInstance()

    mppModel.sourceSetsByName.values.forEach { sourceSet ->
        /* Support for non-mpp Android plugins, which are not aware of our new extension points yet */
        if (shouldDelegateToOtherPlugin(sourceSet)) return@forEach

        val sourceSetModuleIde = KotlinSourceSetModuleId(resolverCtx, gradleModule, sourceSet)
        val sourceSetDataNode = ideModule.findSourceSetNode(sourceSetModuleIde) ?: return@forEach
        val sourceSetDependencies = dependencies[sourceSet.name]

        /* Call into extension points, skipping dependency population of source set if instructed */
        if (
            extensionInstance.beforePopulateSourceSetDependencies(
                extensionContext, sourceSetDataNode, sourceSet, sourceSetDependencies
            ) == KotlinMppGradleProjectResolverExtension.Result.Skip
        ) return@forEach

        /*
        Some dependencies are represented as IdeaKotlinProjectArtifactDependency.
        Such dependencies can be resolved to the actual source sets that built this artifact.
         */
        val projectArtifactDependencyResolver = KotlinProjectArtifactDependencyResolver()
        val resolvedSourceSetDependencies = sourceSetDependencies.flatMap { dependency ->
            if (dependency is IdeaKotlinProjectArtifactDependency)
                projectArtifactDependencyResolver.resolve(ideProject, sourceSetDataNode, dependency)
            else listOf(dependency)
        }.toSet()

        /* Add each resolved dependency */
        val createdDependencyNodes = resolvedSourceSetDependencies.flatMapIndexed { index, dependency ->
            sourceSetDataNode.addDependency(dependency)
                /* The classpath order of the dependencies is given by the order they were sent by the Kotlin Gradle Plugin */
                .onEach { it.data.setOrder(index) }
        }

        /* Calling into extensions, notifying them about all populated dependencies */
        extensionInstance.afterPopulateSourceSetDependencies(
            extensionContext, sourceSetDataNode, sourceSet, resolvedSourceSetDependencies, createdDependencyNodes
        )
    }
}

/**
 * Implementation for older Kotlin Gradle plugins that will use
 * IntelliJ injected code to resolve dependencies
 */
internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesWithoutDependenciesContainer(
    gradleModule: IdeaModule,
    ideProject: DataNode<ProjectData>,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext,
) {
    val context = createKotlinMppPopulateModuleDependenciesContext(
        gradleModule = gradleModule,
        ideProject = ideProject,
        ideModule = ideModule,
        resolverCtx = resolverCtx
    ) ?: return

    populateModuleDependenciesByCompilations(context)
    populateModuleDependenciesByPlatformPropagation(context)
    populateModuleDependenciesBySourceSetVisibilityGraph(context)
}
