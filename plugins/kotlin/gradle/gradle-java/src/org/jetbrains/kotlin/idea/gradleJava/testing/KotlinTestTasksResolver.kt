// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.registry.Registry
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(Int.MIN_VALUE)
open class KotlinTestTasksResolver : AbstractProjectResolverExtension() {

    companion object {
        internal const val ENABLED_REGISTRY_KEY = "kotlin.gradle.testing.enabled"
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModelBuilder::class.java, KotlinTarget::class.java, Unit::class.java)
    }

    override fun populateModuleTasks(
        gradleModule: IdeaModule,
        ideModule: DataNode<ModuleData>,
        ideProject: DataNode<ProjectData>
    ): MutableCollection<TaskData> {
        if (!Registry.`is`(ENABLED_REGISTRY_KEY))
            return super.populateModuleTasks(gradleModule, ideModule, ideProject)

        val mppModel = resolverCtx.getMppModel(gradleModule)
            ?: return super.populateModuleTasks(gradleModule, ideModule, ideProject)

        return postprocessTaskData(mppModel, ideModule, nextResolver.populateModuleTasks(gradleModule, ideModule, ideProject))
    }

    private fun postprocessTaskData(
        mppModel: KotlinMPPGradleModel,
        ideModule: DataNode<ModuleData>,
        originalTaskData: MutableCollection<TaskData>
    ): MutableCollection<TaskData> {
        val testTaskNames = mutableSetOf<String>().apply {
            mppModel.targets.forEach { target ->
                target.testRunTasks.forEach { testTaskModel ->
                    add(testTaskModel.taskName)
                }
            }
        }

        fun buildNewTaskDataMarkedAsTest(original: TaskData): TaskData =
            TaskData(original.owner, original.name, original.linkedExternalProjectPath, original.description).apply {
                group = original.group
                type = original.type
                isInherited = original.isInherited

                isTest = true
            }

        val replacementMap: Map<TaskData, TaskData> = mutableMapOf<TaskData, TaskData>().apply {
            originalTaskData.forEach {
                if (it.name in testTaskNames && !it.isTest) {
                    put(it, buildNewTaskDataMarkedAsTest(it))
                }
            }
        }

        ideModule.children.filter { it.data in replacementMap }.forEach { it.clear(true) }
        replacementMap.values.forEach { ideModule.createChild(ProjectKeys.TASK, it) }

        return originalTaskData.mapTo(arrayListOf()) { replacementMap[it] ?: it }
    }
}