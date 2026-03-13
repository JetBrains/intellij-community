// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.safeCastDataNode
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class KotlinJvmRunTaskData(
    val targetName: String,
    val taskName: String,
    val gradlePluginType: KotlinGradlePluginType,
) {
    companion object {
        private const val KOTLIN_KMP_JVM_RUN_CLASS_NAME = "org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun"
        private const val KOTLIN_JVM_RUN_CLASS_NAME = "org.gradle.api.tasks.JavaExec"

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
            val mainModuleDataNode = CachedModuleDataFinder.findMainModuleData(module) ?: return null

            val kotlinGradlePluginType = KotlinGradlePluginType.getPluginType(mainModuleDataNode) ?: return null
            val kotlinRunClassName = when (kotlinGradlePluginType) {
                KotlinGradlePluginType.Jvm -> {
                    KOTLIN_JVM_RUN_CLASS_NAME.takeIf {
                        // For modules using jvm plugin, only continue if CMP plugin is also used,
                        // since CMP plugin adds the required Gradle tasks
                        getGradleExtensions(mainModuleDataNode)?.any {
                            it.name == "compose" && it.typeFqn == "org.jetbrains.compose.ComposeExtension"
                        } == true
                    } ?: return null
                }

                KotlinGradlePluginType.Multiplatform -> KOTLIN_KMP_JVM_RUN_CLASS_NAME
            }

            /* Find all run carrier tasks (tasks implementing KotlinJvmRun */
            val allKotlinJvmRunTasks = ExternalSystemApiUtil.findAll(mainModuleDataNode, ProjectKeys.TASK)
                .filter { it.data.type == kotlinRunClassName }
                .ifEmpty { return null }

            return when (kotlinGradlePluginType) {
                KotlinGradlePluginType.Multiplatform -> getKmpPluginRunTask(module, mainModuleDataNode, allKotlinJvmRunTasks)
                KotlinGradlePluginType.Jvm -> getJvmPluginRunTask(allKotlinJvmRunTasks)
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
                    .filter { target -> taskNameWithoutLocation.equals("${target.data.externalName}Run", ignoreCase = true) }
                    .firstOrNull { target -> target.data.moduleIds.any { targetModuleId -> targetModuleId in sourceSetModuleIds } }
                    ?: return@firstNotNullOfOrNull null
                KotlinJvmRunTaskData(target.data.externalName, taskName, KotlinGradlePluginType.Multiplatform)
            }
        }

        private fun getJvmPluginRunTask(allKotlinJvmRunTasks: List<DataNode<TaskData>>): KotlinJvmRunTaskData? =
            allKotlinJvmRunTasks.firstNotNullOfOrNull { runTask ->
                val taskName = runTask.data.name.let { if (it.startsWith(':')) it else ":$it" }
                val taskNameWithoutLocation = taskName.substringAfterLast(':')
                if (taskNameWithoutLocation != "run") return@firstNotNullOfOrNull null
                return KotlinJvmRunTaskData("jvm", taskName, KotlinGradlePluginType.Jvm)
            }

    }
}