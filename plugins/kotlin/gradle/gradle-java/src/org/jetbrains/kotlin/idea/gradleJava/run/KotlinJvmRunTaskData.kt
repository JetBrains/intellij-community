// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.safeCastDataNode
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class KotlinJvmRunTaskData(val targetName: String, val taskName: String) {
    companion object {
        private const val KOTLIN_JVM_RUN_CLASS_NAME = "org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun"

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

            /* Find all run carrier tasks (tasks implementing KotlinJvmRun */
            val allKotlinJvmRunTasks = ExternalSystemApiUtil.findAll(mainModuleDataNode, ProjectKeys.TASK)
                .filter { it.data.type == KOTLIN_JVM_RUN_CLASS_NAME }
                .ifEmpty { return null }

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
                val target = allKotlinTargetDataNodes
                    .filter { target -> runTask.data.name.lowercase() == "${target.data.externalName}Run".lowercase() }
                    .firstOrNull { target -> target.data.moduleIds.any { targetModuleId -> targetModuleId in sourceSetModuleIds } }
                    ?: return@firstNotNullOfOrNull null
                KotlinJvmRunTaskData(target.data.externalName, runTask.data.name)
            }
        }
    }
}