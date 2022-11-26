// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.project.modules
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.kotlin.platform.isCommon
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

    override fun findExistingConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings? {
        val existingConfiguration = super.findExistingConfiguration(context)
        if (existingConfiguration == null) {
            // it might be cheaper to check existing configuration than to determine a module platform on cold start
            return null
        }

        val module = context.module ?: return null
        if (module.platform.isCommon()) {
            return null
        }

        return existingConfiguration
    }

    override fun getAllTestsTaskToRun(
        context: ConfigurationContext,
        element: PsiElement,
        chosenElements: List<PsiElement>
    ): List<TestTasksToRun> {

        if (context.project.modules.none { it.isMultiPlatformModule })
            return emptyList()

        val wildcardFilter = createTestWildcardFilter()
        val tasks = mppTestTasksChooser.listAvailableTasks(listOf(element))

        return tasks.map { TestTasksToRun(it, wildcardFilter) }
    }
}