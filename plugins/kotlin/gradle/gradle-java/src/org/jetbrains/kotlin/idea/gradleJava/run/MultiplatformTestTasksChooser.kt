// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.idea.base.facet.externalSystemTestRunTasks
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.plugins.gradle.execution.test.runner.SourcePath
import org.jetbrains.plugins.gradle.execution.test.runner.TestName
import org.jetbrains.plugins.gradle.execution.test.runner.TestTasksChooser
import org.jetbrains.plugins.gradle.execution.test.runner.getSourceFile
import org.jetbrains.plugins.gradle.util.TasksToRun

private typealias TaskFilter = (ExternalSystemTestRunTask) -> Boolean

class MultiplatformTestTasksChooser : TestTasksChooser() {
    companion object {
        fun createContext(context: DataContext, locationName: String?): DataContext {
            return contextWithLocationName(context, locationName)
        }
    }

    fun multiplatformChooseTasks(
        project: Project,
        dataContext: DataContext,
        elements: Iterable<PsiElement>,
        contextualSuffix: String? = null, // like "js, browser, HeadlessChrome85.0.4183, MacOSX10.14.6"
        handler: (List<Map<SourcePath, TasksToRun>>) -> Unit
    ) {
        val testTasks = resolveTestTasks(elements, contextualFilter(contextualSuffix))
        when {
            testTasks.isEmpty() -> super.chooseTestTasks(project, dataContext, elements, handler)
            testTasks.size == 1 -> handler(testTasks.values.toList())
            else -> chooseTestTasks(project, dataContext, testTasks, handler)
        }
    }

    fun listAvailableTasks(elements: Iterable<PsiElement>): List<TasksToRun> {
        return resolveTestTasks(elements, contextualFilter()).values.flatMap { it.values }
    }

    private fun contextualFilter(contextualSuffix: String? = null): TaskFilter {
        val parts = contextualSuffix?.split(", ")
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: return { _ -> true }

        val targetName = parts[0]

        if (parts.size == 1) {
            return { it.targetName == targetName }
        }

        val taskPrefix = targetName + parts[1].capitalizeAsciiOnly()

        return { it.targetName == targetName && it.taskName.startsWith(taskPrefix) }
    }

    private fun resolveTestTasks(
        elements: Iterable<PsiElement>,
        taskFilter: TaskFilter
    ): Map<TestName, Map<SourcePath, TasksToRun>> {
        val tasks = mutableMapOf<TestName, MutableMap<SourcePath, TasksToRun>>()


        for (element in elements) {
            val module = element.module ?: continue
            val sourceFile = getSourceFile(element) ?: continue

            module.externalSystemTestRunTasks()
                .filter { taskFilter(it) }
                .groupBy { it.targetName }
                .forEach { (targetName, targetTasks) ->
                    val singleTask = targetTasks.size == 1
                    targetTasks.forEach { task ->
                        //KT-56540: by default, android tests are being launched on a local JVM
                        val isAndroidTarget = task.kotlinPlatformId == KotlinPlatform.ANDROID.id
                        val presentableName = if (isAndroidTarget) {
                            if (singleTask) "$targetName (local)" else "$targetName (local :${task.taskName})"
                        } else {
                            if (singleTask) targetName else "$targetName (:${task.taskName})"
                        }
                        val tasksMap = tasks.getOrPut(presentableName) { LinkedHashMap() }
                        tasksMap[sourceFile.path] = TasksToRun.Impl(presentableName, getTaskNames(task))
                    }
                }
        }

        return tasks
    }

    override fun <T> chooseTestTasks(
        project: Project,
        context: DataContext,
        testTasks: Map<TestName, T>,
        consumer: (List<T>) -> Unit
    ) {
        if (isUnitTestMode()) {
            val result = mutableListOf<T>()

            for (tasks in testTasks.values) {
                result += tasks
            }

            consumer(result)
            return
        }

        super.chooseTestTasks(project, context, testTasks, consumer)
    }

    private fun getTaskNames(task: ExternalSystemRunTask): List<String> {
        // ExternalSystemRunTask.externalSystemProjectId is a fully qualified task name prefix. For the root project it's ":", for nested
        // ones it's ":<project>:<sub-project>[:<etc>]"
        val taskNamePrefix = task.externalSystemProjectId.takeIf { it == ":" } ?: (task.externalSystemProjectId + ":")
        return listOf("${taskNamePrefix}clean${task.taskName.capitalizeAsciiOnly()}", "${taskNamePrefix}${task.taskName}")
    }
}
