// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils.isAndroidStudio
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.safeCastDataNode
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class KotlinJvmRunTaskData(
    val targetName: String,
    val taskName: String,
    val isComposeGradlePluginConfigured: Boolean,
) {
    companion object {
        private const val KOTLIN_KMP_JVM_RUN_CLASS_NAME = "org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun"
        private const val JAVA_EXEC_RUN_CLASS_NAME = "org.gradle.api.tasks.JavaExec"
        private const val JVM_SOURCE_SET_NAME = "jvm"
        private const val COMPOSE_RUN_TASK_NAME = "run"
        private const val KMP_RUN_TASK_SUFFIX = "Run"

        /**
         * Will return the *first* suitable KotlinJvmRun task that is suitable for this module.
         * Note: The run gutter will also support running test in common source sets (like commonMain), if those SourceSets
         * will be included in the jvm target offering the run task!
         *
         * Note: There might be more than just the 'first' run task suitable, e.g. in common Source Sets that participate in multiple
         * jvm targets. However, since this is avery advanced use case for now, and is scheduled for deprecation, this case
         * is omitted in order to keep it simple.
         */
        fun findSuitableKotlinJvmRunTask(module: Module): KotlinJvmRunTaskData? {
            val mainModuleDataNode = module.findMainModuleCachedData() ?: return null

            val kotlinGradlePluginType = KotlinGradlePluginType.getPluginType(mainModuleDataNode) ?: return null
            val usesComposeGradlePlugin = usesComposeGradlePlugin(mainModuleDataNode)
            val usesKmpPlugin = kotlinGradlePluginType == KotlinGradlePluginType.Multiplatform
            val runClassName = when {
                usesComposeGradlePlugin -> JAVA_EXEC_RUN_CLASS_NAME
                usesKmpPlugin -> KOTLIN_KMP_JVM_RUN_CLASS_NAME
                else -> return null
            }

            /* Find all run carrier tasks (tasks implementing KotlinJvmRun) */
            val allGradleRunTasks = ExternalSystemApiUtil.findAll(mainModuleDataNode, ProjectKeys.TASK).asSequence()
            val allKotlinJvmRunTasks = allGradleRunTasks
                .filter { it.data.type == runClassName }
                .toList()

            return when {
                isAndroidStudio && allKotlinJvmRunTasks.isEmpty() ->
                    inferKotlinJvmRunTask(allGradleRunTasks, mainModuleDataNode, module, usesComposeGradlePlugin)
                usesComposeGradlePlugin -> getCmpPluginRunTask(allKotlinJvmRunTasks)
                usesKmpPlugin -> getKmpPluginRunTask(module, mainModuleDataNode, allKotlinJvmRunTasks)
                else -> null
            }

        }

        /**
         * In Android Studio only test run tasks are loaded on Gradle import.
         * So we try to infer the execution run task name based on the test run tasks that are available.
         */
        private fun inferKotlinJvmRunTask(
            allGradleRunTasks: Sequence<DataNode<TaskData?>?>,
            mainModuleDataNode: DataNode<out ModuleData>,
            module: Module,
            usesComposeGradlePlugin: Boolean,
        ): KotlinJvmRunTaskData? {
            val location = mainModuleDataNode.data.id.let {
                if (it.startsWith(':')) it else ""
            }

            when (usesComposeGradlePlugin) {
                true -> {
                    val expectedTestTask = if (location.isNotBlank()) "$location:test" else "test"
                    val testRunTaskFound = allGradleRunTasks
                        .map { it?.data?.name }
                        .contains(expectedTestTask)
                    if (!testRunTaskFound) return null
                    return KotlinJvmRunTaskData(
                        targetName = JVM_SOURCE_SET_NAME,
                        taskName = "$location:$COMPOSE_RUN_TASK_NAME",
                        isComposeGradlePluginConfigured = true
                    )
                }

                false -> {
                    val sourceSetName = module.name
                        .substringAfterLast('.')
                        .takeIf { it.endsWith("Main") }
                        ?.removeSuffix("Main")
                        ?: return null
                    val expectedTestTask = if (location.isNotBlank()) "$location:${sourceSetName}Test" else "${sourceSetName}Test"
                    val testRunTaskFound = allGradleRunTasks
                        .map { it?.data?.name }
                        .contains(expectedTestTask)
                    if (!testRunTaskFound) return null
                    return KotlinJvmRunTaskData(
                        targetName = sourceSetName,
                        taskName = "$location:${sourceSetName}$KMP_RUN_TASK_SUFFIX",
                        isComposeGradlePluginConfigured = false
                    )
                }
            }
        }

        private fun getKmpPluginRunTask(
            module: Module,
            mainModuleDataNode: DataNode<out ModuleData>,
            allKotlinJvmRunTasks: List<DataNode<TaskData>>
        ): KotlinJvmRunTaskData? {

            /*
            As the passed 'module' can also be a common Source Set (like commonMain),
            We collect all SourceSets that declare a dependsOn as well. If any of those Source Sets can be executed
            by the run task, then the Source Set represented by 'module' can also!
            */
            val sourceSetDataNode = CachedModuleDataFinder.findModuleData(module)?.safeCastDataNode<GradleSourceSetData>() ?: return null
            val allSourceSetDataNodes = ExternalSystemApiUtil.findAll(mainModuleDataNode, GradleSourceSetData.KEY)
            val sourceSetWithDependingSourceSetDataNodes = sourceSetDataNode.withClosure { currentSourceSetDataNode ->
                val currentKotlinSourceSetData = currentSourceSetDataNode.kotlinSourceSetData
                allSourceSetDataNodes.filter { potentialRelevantSourceSetDataNode ->
                    val kotlinSourceSetData = potentialRelevantSourceSetDataNode.kotlinSourceSetData ?: return@filter false
                    currentKotlinSourceSetData?.sourceSetInfo?.moduleId in kotlinSourceSetData.sourceSetInfo.dependsOn
                }
            }

            /*
            moduleIds of all Source Sets that are associated with the 'module':
            Id of the module, as well as all moduleIds of Source Sets that declared a dependsOn this module.
             */
            val sourceSetModuleIds = sourceSetWithDependingSourceSetDataNodes
                .mapNotNull { it.kotlinSourceSetData?.sourceSetInfo?.moduleId }
                .toSet()

            val allKotlinTargetDataNodes = ExternalSystemApiUtil.findAll(mainModuleDataNode, KotlinTargetData.KEY)

            /*
            Select first runTask, which can includes this 'module'
            1) We ensure the runTask belongs to the target
            2) We ensure that the 'module' belongs to the target
            */
            return allKotlinJvmRunTasks.firstNotNullOfOrNull { runTask ->
                val taskName = runTask.data.name.let { if (it.startsWith(':')) it else ":$it" }
                val taskNameWithoutLocation = taskName.substringAfterLast(':')
                val target = allKotlinTargetDataNodes
                    .filter { target -> taskNameWithoutLocation.equals("${target.data.externalName}$KMP_RUN_TASK_SUFFIX", ignoreCase = true) }
                    .firstOrNull { target -> target.data.moduleIds.any { targetModuleId -> targetModuleId in sourceSetModuleIds } }
                    ?: return@firstNotNullOfOrNull null
                KotlinJvmRunTaskData(target.data.externalName, taskName, isComposeGradlePluginConfigured = false)
            }
        }

        private fun getCmpPluginRunTask(allKotlinJvmRunTasks: List<DataNode<TaskData>>): KotlinJvmRunTaskData? =
            allKotlinJvmRunTasks.firstNotNullOfOrNull { runTask ->
                val taskName = runTask.data.name.let { if (it.startsWith(':')) it else ":$it" }
                val taskNameWithoutLocation = taskName.substringAfterLast(':')
                if (taskNameWithoutLocation != COMPOSE_RUN_TASK_NAME) return@firstNotNullOfOrNull null
                return KotlinJvmRunTaskData(JVM_SOURCE_SET_NAME, taskName, isComposeGradlePluginConfigured = true)
            }

    }
}