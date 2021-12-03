// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.caches.project.isMPPModule
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
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

        if (context.project.allModules().none { it.isMPPModule })
            return emptyList()

        val psiLocation = context.psiLocation ?: return emptyList()
        val sourceElement = getSourceElement(context.module, psiLocation) ?: return emptyList()
        val wildcardFilter = createTestFilterFrom(element)
        val tasks = mppTestTasksChooser.listAvailableTasks(listOf(sourceElement))

        return tasks.map { TestTasksToRun(it, wildcardFilter) }
    }
}