// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.mpp.DistinctIdKotlinDependenciesPreprocessor
import org.jetbrains.kotlin.idea.gradle.configuration.mpp.KotlinDependenciesPreprocessor
import org.jetbrains.kotlin.idea.gradle.configuration.mpp.plus
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Companion.modifyDependenciesOnMppModules
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleJava.configuration.klib.KotlinNativeLibrariesDependencySubstitutor
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import com.intellij.openapi.util.Pair as IntelliJPair


internal typealias ModuleId = String
internal typealias ArtifactPath = String

data class KotlinMppPopulateModuleDependenciesContext(
    val resolverCtx: ProjectResolverContext,
    val mppModel: KotlinMPPGradleModel,
    val gradleModule: IdeaModule,
    val ideProject: DataNode<ProjectData>,
    val ideModule: DataNode<ModuleData>,
    val dependenciesPreprocessor: KotlinDependenciesPreprocessor,
    val sourceSetMap: Map<ModuleId, IntelliJPair<DataNode<GradleSourceSetData>, ExternalSourceSet>>,
    val artifactsMap: Map<ArtifactPath, ModuleId>,
    val processedModuleIds: MutableSet<ModuleId> = mutableSetOf(),
)

fun createKotlinMppPopulateModuleDependenciesContext(
    gradleModule: IdeaModule,
    ideProject: DataNode<ProjectData>,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext
): KotlinMppPopulateModuleDependenciesContext? {
    val mppModel = resolverCtx.getMppModel(gradleModule) ?: return null
    mppModel.dependencyMap.values.modifyDependenciesOnMppModules(ideProject, resolverCtx)

    val sourceSetMap = ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS) ?: return null
    val artifactsMap = ideProject.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS) ?: return null
    val dependenciesPreprocessor = KotlinNativeLibrariesDependencySubstitutor(mppModel, gradleModule, resolverCtx)
        .plus(DistinctIdKotlinDependenciesPreprocessor)

    return KotlinMppPopulateModuleDependenciesContext(
        resolverCtx = resolverCtx,
        mppModel = mppModel,
        gradleModule = gradleModule,
        ideProject = ideProject,
        ideModule = ideModule,
        dependenciesPreprocessor = dependenciesPreprocessor,
        sourceSetMap = sourceSetMap,
        artifactsMap = artifactsMap,
    )
}

fun KotlinMppPopulateModuleDependenciesContext.getDependencies(module: KotlinComponent): List<KotlinDependency> {
    return dependenciesPreprocessor(module.dependencies.mapNotNull { id -> mppModel.dependencyMap[id] })
}

fun KotlinMppPopulateModuleDependenciesContext.getRegularDependencies(sourceSet: KotlinSourceSet): List<KotlinDependency> {
    return dependenciesPreprocessor(sourceSet.regularDependencies.mapNotNull { id -> mppModel.dependencyMap[id] })
}

fun KotlinMppPopulateModuleDependenciesContext.getIntransitiveDependencies(sourceSet: KotlinSourceSet): List<KotlinDependency> {
    return dependenciesPreprocessor(sourceSet.intransitiveDependencies.mapNotNull { id -> mppModel.dependencyMap[id] })
}
