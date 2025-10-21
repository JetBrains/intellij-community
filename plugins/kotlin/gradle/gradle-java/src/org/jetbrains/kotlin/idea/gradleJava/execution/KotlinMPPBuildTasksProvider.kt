// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.execution

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.task.BuildTask
import com.intellij.task.ModuleBuildTask
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.idea.facet.KotlinFacet.Companion.get
import org.jetbrains.kotlin.idea.facet.KotlinFacetModificationTracker
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.execution.build.GradleBuildTasksProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript
import java.util.function.BiConsumer

class KotlinMPPBuildTasksProvider : GradleBuildTasksProvider {
    override fun isApplicable(buildTask: BuildTask): Boolean {
        val moduleBuildTask = buildTask as? ModuleBuildTask ?: return false
        val module = moduleBuildTask.module
        return isProjectWithNativeSourceOrCommonProductionSourceModules(module.project)
    }

    override fun addBuildTasks(
        buildTask: BuildTask,
        buildTasksConsumer: Consumer<ExternalTaskPojo>,
        initScriptConsumer: BiConsumer<String, VersionSpecificInitScript>
    ) {
        if (!isApplicable(buildTask)) return

        val moduleBuildTask = buildTask as? ModuleBuildTask ?: return
        val module = moduleBuildTask.module

        val sourceSetName = GradleProjectResolverUtil.getSourceSetName(module) ?: return
        val rootProjectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return

        val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module) ?: return
        val taskDataList = gradleModuleData.findAll(ProjectKeys.TASK).filter { !it.isInherited }
        if (taskDataList.isEmpty()) return
        val gradleIdentityPath = gradleModuleData.gradleIdentityPathOrNull ?: return
        val taskPathPrefix = gradleIdentityPath.trimEnd(':') + ":"
        val gradleModuleTasks = taskDataList.map { it.name.removePrefix(taskPathPrefix) }

        fun Consumer<ExternalTaskPojo>.send(name: String) = this.consume(ExternalTaskPojo(name, rootProjectPath, null))
        if (isNativeSourceModule(module)) {
            // Add tasks for Kotlin/Native.
            findNativeGradleBuildTasks(gradleModuleTasks, sourceSetName)
                .map { taskPathPrefix + it }
                .forEach { buildTasksConsumer.send(it) }
        } else {
            // Add tasks for compiling metadata.
            findMetadataBuildTasks(gradleModuleTasks, sourceSetName)
                .map { taskPathPrefix + it }
                .forEach { buildTasksConsumer.send(it) }
        }
    }

    private fun isProjectWithNativeSourceOrCommonProductionSourceModules(project: Project): Boolean {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            CachedValueProvider.Result(
                ContainerUtil.exists(ModuleManager.getInstance(project).modules) {
                    isNativeSourceModule(it) || isCommonProductionSourceModule(it)
                },
                KotlinFacetModificationTracker.getInstance(project)
            )
        }
    }

    private fun isNativeSourceModule(module: Module): Boolean {
        val kotlinFacet = get(module) ?: return false
        val platform = kotlinFacet.configuration.settings.targetPlatform ?: return false
        return platform.isNative()
    }

    private fun isCommonProductionSourceModule(module: Module): Boolean {
        val kotlinFacet = get(module) ?: return false
        val facetSettings = kotlinFacet.configuration.settings
        if (facetSettings.isTestModule) return false
        val platform = facetSettings.targetPlatform ?: return false
        return platform.isCommon()
    }

    private fun findNativeGradleBuildTasks(gradleTasks: Collection<String>, sourceSetName: String): Collection<String> {
        // First, attempt to find Kotlin/Native convention Gradle task that unites all outputType-specific build tasks.
        val conventionGradleTask = sourceSetName + "Binaries"
        if (gradleTasks.contains(conventionGradleTask)) {
            return listOf(conventionGradleTask)
        }

        // If convention task not found, then attempt to find all appropriate build tasks for the given source set.
        val linkPrefixes: Collection<String>
        val targetName: String
        if (sourceSetName.endsWith("Main")) {
            targetName = sourceSetName.substringBeforeLast("Main")
            linkPrefixes = listOf("link", "linkMain")
        } else if (sourceSetName.endsWith("Test")) {
            targetName = sourceSetName.substringBeforeLast("Test")
            linkPrefixes = listOf("linkTest")
        } else {
            targetName = sourceSetName
            linkPrefixes = listOf("link")
        }
        return linkPrefixes // get base task name (without disambiguation classifier)
            .map { linkPrefix ->
                linkPrefix + targetName.capitalize()
            } // find all Gradle tasks that start with base task name
            .flatMap { nativeTaskName ->
                gradleTasks.filter { taskName -> taskName.startsWith(nativeTaskName) }
            }
    }

    private fun findMetadataBuildTasks(gradleTasks: Collection<String>, sourceSetName: String): Collection<String?> {
        if ("commonMain" == sourceSetName) {
            val metadataTaskName = "metadataMainClasses"
            if (gradleTasks.contains(metadataTaskName)) {
                return listOf(metadataTaskName)
            }
        }
        return emptyList<String>()
    }
}