// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.project.isMPPModule
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.plugins.gradle.execution.test.runner.AllInDirectoryGradleConfigurationProducer
import org.jetbrains.plugins.gradle.util.createTestWildcardFilter

class KotlinMultiplatformAllInDirectoryConfigurationProducer
    : AllInDirectoryGradleConfigurationProducer() {

    private val mppTestTasksChooser = MultiplatformTestTasksChooser()


    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return !other.isProducedBy(KotlinMultiplatformAllInPackageConfigurationProducer::class.java) && super.isPreferredConfiguration(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return !other.isProducedBy(KotlinMultiplatformAllInPackageConfigurationProducer::class.java) && super.shouldReplace(self, other)
    }

    override fun getAllTestsTaskToRun(
        context: ConfigurationContext,
        element: PsiElement,
        chosenElements: List<PsiElement>
    ): List<TestTasksToRun> {

        if (context.project.allModules().none { it.isMPPModule })
            return emptyList()

        var result: List<TestTasksToRun> = emptyList()
        mppTestTasksChooser.multiplatformChooseTasks(context.project, context.dataContext, listOf(element)) { tasks ->
            val wildcardFilter = createTestWildcardFilter()
            val tasksToRuns = tasks
                .flatMap { it.values }
                .map { TestTasksToRun(it, wildcardFilter) }
            result = tasksToRuns
        }
        return result
    }
}