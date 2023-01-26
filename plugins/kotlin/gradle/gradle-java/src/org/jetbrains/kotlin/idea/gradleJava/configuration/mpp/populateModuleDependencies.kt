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

    mppModel.sourceSetsByName.values.forEach { sourceSet ->
        val sourceSetModuleIde = KotlinSourceSetModuleId(resolverCtx, gradleModule, sourceSet)
        val sourceSetDataNode = ideModule.findSourceSetNode(sourceSetModuleIde) ?: return@forEach

        /*
        Some dependencies are represented as IdeaKotlinProjectArtifactDependency.
        Such dependencies can be resolved to the actual source sets that built this artifact.
         */
        val projectArtifactDependencyResolver = KotlinProjectArtifactDependencyResolver()
        val resolvedDependencies = dependencies[sourceSet.name].flatMap { dependency ->
            if (dependency is IdeaKotlinProjectArtifactDependency)
                projectArtifactDependencyResolver.resolve(ideProject, sourceSetDataNode, dependency)
            else listOf(dependency)
        }

        /*
        Add each resolved dependency
         */
        resolvedDependencies.forEachIndexed { index, dependency ->
            sourceSetDataNode.addDependency(dependency)
                /* The classpath order of the dependencies is given by the order they were sent by the Kotlin Gradle Plugin */
                .forEach { it.data.setOrder(index) }
        }
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
