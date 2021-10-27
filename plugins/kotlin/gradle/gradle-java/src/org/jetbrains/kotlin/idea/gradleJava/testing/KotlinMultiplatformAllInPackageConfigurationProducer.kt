// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

class KotlinMultiplatformAllInPackageConfigurationProducer: AllInPackageGradleConfigurationProducer() {

    private val mppTestTasksChooser = MultiplatformTestTasksChooser()

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isProducedBy(KotlinMultiplatformAllInDirectoryConfigurationProducer::class.java) || super.isPreferredConfiguration(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isProducedBy(KotlinMultiplatformAllInDirectoryConfigurationProducer::class.java) || super.shouldReplace(self, other)
    }

    override fun getAllTestsTaskToRun(
        context: ConfigurationContext,
        element: PsiPackage,
        chosenElements: List<PsiPackage>
    ): List<TestTasksToRun> {

        var result: List<TestTasksToRun> = emptyList()

        val psiLocation = context.psiLocation ?: return result
        val sourceElement = getSourceElement(context.module, psiLocation) ?: return result

        mppTestTasksChooser.multiplatformChooseTasks(context.project, context.dataContext, listOf(sourceElement)) { tasks ->
            val wildcardFilter = createTestFilterFrom(element)
            val tasksToRuns = tasks
                .flatMap { it.values }
                .map { TestTasksToRun(it, wildcardFilter) }
            result = tasksToRuns
        }
        return result
    }
}