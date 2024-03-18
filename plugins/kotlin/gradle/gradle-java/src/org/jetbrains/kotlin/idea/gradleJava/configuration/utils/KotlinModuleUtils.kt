// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.utils

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.config.ExternalSystemNativeMainRunTask
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.compilationFullName
import org.jetbrains.kotlin.idea.gradleTooling.resolveAllDependsOnSourceSets
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

object KotlinModuleUtils {

    fun KotlinComponent.fullName(simpleName: String = name) = when (this) {
        is KotlinCompilation -> compilationFullName(simpleName, disambiguationClassifier)
        else -> simpleName
    }

    private fun KotlinTarget.testTasksFor(compilation: KotlinCompilation) = testRunTasks.filter { task ->
        when (platform) {
            KotlinPlatform.ANDROID -> task.taskName.endsWith(compilation.name, true)
            else -> task.compilationName == compilation.name
        }
    }

    fun calculateRunTasks(
        mppModel: KotlinMPPGradleModel,
        gradleModule: IdeaModule,
        resolverCtx: ProjectResolverContext
    ): Map<KotlinSourceSet, Collection<ExternalSystemRunTask>> {
        val sourceSetToRunTasks: MutableMap<KotlinSourceSet, MutableCollection<ExternalSystemRunTask>> = HashMap()
        val dependsOnReverseGraph: MutableMap<String, MutableSet<KotlinSourceSet>> = HashMap()
        mppModel.targets.forEach { target ->
            target.compilations.forEach { compilation ->
                val testRunTasks = target.testTasksFor(compilation)
                    .map {
                        ExternalSystemTestRunTask(
                            it.taskName,
                            gradleModule.gradleProject.path,
                            target.name,
                            target.platform.id
                        )
                    }
                val nativeMainRunTasks = target.nativeMainRunTasks
                    .filter { task -> task.compilationName == compilation.name }
                    .map {
                        ExternalSystemNativeMainRunTask(
                            it.taskName,
                            getKotlinModuleId(gradleModule, compilation, resolverCtx),
                            target.name,
                            it.entryPoint,
                            it.debuggable
                        )
                    }
                val allRunTasks = testRunTasks + nativeMainRunTasks
                compilation.declaredSourceSets.forEach { sourceSet ->
                    sourceSetToRunTasks.getOrPut(sourceSet) { LinkedHashSet() } += allRunTasks
                    mppModel.resolveAllDependsOnSourceSets(sourceSet).forEach { dependentModule ->
                        dependsOnReverseGraph.getOrPut(dependentModule.name) { LinkedHashSet() } += sourceSet
                    }
                }
            }
        }
        mppModel.sourceSetsByName.forEach { (sourceSetName, sourceSet) ->
            dependsOnReverseGraph[sourceSetName]?.forEach { dependingSourceSet ->
                sourceSetToRunTasks.getOrPut(sourceSet) { LinkedHashSet() } += sourceSetToRunTasks[dependingSourceSet] ?: emptyList()
            }
        }
        return sourceSetToRunTasks
    }

    fun getGradleModuleQualifiedName(
        resolverCtx: ProjectResolverContext,
        gradleModule: IdeaModule,
        simpleName: String
    ): String = GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule) + ":" + simpleName

    fun getKotlinModuleId(
        gradleModule: IdeaModule, kotlinComponent: KotlinComponent, resolverCtx: ProjectResolverContext
    ) = getGradleModuleQualifiedName(resolverCtx, gradleModule, kotlinComponent.fullName())

    fun getInternalModuleName(
        gradleModule: IdeaModule,
        externalProject: ExternalProject,
        resolverCtx: ProjectResolverContext,
        fullName: String
    ): String {
        val delimiter: String
        val moduleName = StringBuilder()

        val buildSrcGroup = resolverCtx.buildSrcGroup
        if (resolverCtx.isUseQualifiedModuleNames) {
            delimiter = "."
            if (StringUtil.isNotEmpty(buildSrcGroup)) {
                moduleName.append(buildSrcGroup).append(delimiter)
            }
            moduleName.append(
                gradlePathToQualifiedName(
                    gradleModule.project.name,
                    externalProject.qName
                )
            )
        } else {
            delimiter = "_"
            if (StringUtil.isNotEmpty(buildSrcGroup)) {
                moduleName.append(buildSrcGroup).append(delimiter)
            }
            moduleName.append(gradleModule.name)
        }
        moduleName.append(delimiter)
        moduleName.append(fullName)
        return PathUtilRt.suggestFileName(moduleName.toString(), true, false)
    }

    private fun gradlePathToQualifiedName(
        rootName: String,
        gradlePath: String
    ): String {
        return ((if (gradlePath.startsWith(":")) "$rootName." else "")
                + Arrays.stream(gradlePath.split(":".toRegex()).toTypedArray())
            .filter { s: String -> s.isNotEmpty() }
            .collect(Collectors.joining(".")))
    }
}