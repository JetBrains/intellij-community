// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.testing

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.project.modules
import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.gradleJava.extensions.KotlinMultiplatformCommonProducersProvider
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.kotlin.idea.gradleJava.run.isProvidedByMultiplatformProducer
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

class KotlinMultiplatformAllInPackageConfigurationProducer :
    KotlinAllInPackageGradleConfigurationProducer(),
    KotlinMultiplatformCommonProducersProvider {

    private val mppTestTasksChooser = MultiplatformTestTasksChooser()

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isProducedBy(KotlinMultiplatformAllInDirectoryConfigurationProducer::class.java) ||
                super.isPreferredConfiguration(self, other) && !other.isProvidedByMultiplatformProducer()
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isProducedBy(KotlinMultiplatformAllInDirectoryConfigurationProducer::class.java) ||
                super.shouldReplace(self, other) && !other.isProvidedByMultiplatformProducer()
    }

    override fun getAllTestsTaskToRun(
        context: ConfigurationContext,
        element: PsiPackage,
        chosenElements: List<PsiPackage>
    ): List<TestTasksToRun> {

        if (context.project.modules.none { it.isMultiPlatformModule })
            return emptyList()

        val psiLocation = context.psiLocation ?: return emptyList()
        val module = context.module ?: return emptyList()
        val sourceElement = getSourceElement(module, psiLocation) ?: return emptyList()
        val wildcardFilter = createTestFilterFrom(element)
        val tasks = mppTestTasksChooser.listAvailableTasks(listOf(sourceElement))

        return tasks.map { TestTasksToRun(it, wildcardFilter) }
    }

    override fun isProducedByCommonProducer(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(this.javaClass)
    }

    override fun findExistingConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings? {
        val existingConfiguration = super.findExistingConfiguration(context)
        if (existingConfiguration == null) {
            // it might be cheaper to check existing configuration than to determine a module platform on cold start
            return null
        }

        val module = context.module ?: return null
        if (module.platform.isCommon() || module.isMultiPlatformModule) {
            return null
        }

        return existingConfiguration
    }
}