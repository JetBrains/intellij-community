// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.InheritorChooser
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.gradle.run.KotlinGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

abstract class AbstractKotlinMultiplatformTestClassGradleConfigurationProducer : AbstractKotlinTestClassGradleConfigurationProducer() {
    override val forceGradleRunner: Boolean get() = true
    override val hasTestFramework: Boolean get() = true

    private val mppTestTasksChooser = MultiplatformTestTasksChooser()

    abstract fun isApplicable(module: Module, platform: TargetPlatform): Boolean

    final override fun isApplicable(module: Module): Boolean {
        if (!module.isNewMultiPlatformModule) {
            return false
        }

        val platform = module.platform ?: return false
        return isApplicable(module, platform)
    }

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isJpsJunitConfiguration()
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return other.isJpsJunitConfiguration()
    }

    override fun getAllTestsTaskToRun(
        context: ConfigurationContext,
        element: PsiClass,
        chosenElements: List<PsiClass>
    ): List<TestTasksToRun> {
        val tasks = mppTestTasksChooser.listAvailableTasks(listOf(element))
        val wildcardFilter = createTestFilterFrom(element)
        return tasks.map { TestTasksToRun(it, wildcardFilter) }
    }

    override fun onFirstRun(fromContext: ConfigurationFromContext, context: ConfigurationContext, performRunnable: Runnable) {
        val inheritorChooser: InheritorChooser = object : InheritorChooser() {
            override fun runForClasses(classes: List<PsiClass>, method: PsiMethod?, context: ConfigurationContext, runnable: Runnable) {
                chooseTestClassConfiguration(fromContext, context, runnable, classes)
            }

            override fun runForClass(aClass: PsiClass, psiMethod: PsiMethod?, context: ConfigurationContext, runnable: Runnable) {
                chooseTestClassConfiguration(fromContext, context, runnable, listOf(aClass))
            }
        }

        val sourceElement = fromContext.sourceElement as PsiClass
        if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, null, sourceElement)) {
            return
        }

        chooseTestClassConfiguration(fromContext, context, performRunnable, listOf(sourceElement))
    }

    private fun chooseTestClassConfiguration(
        fromContext: ConfigurationFromContext,
        context: ConfigurationContext,
        performRunnable: Runnable,
        classes: List<PsiClass>
    ) {
        val locationName = classes.singleOrNull()?.name
        val dataContext = MultiplatformTestTasksChooser.createContext(context.dataContext, locationName)

        mppTestTasksChooser.multiplatformChooseTasks(context.project, dataContext, classes) { tasks ->
            val configuration = fromContext.configuration as GradleRunConfiguration
            val settings = configuration.settings

            val createFilter = { clazz: PsiClass -> createTestFilterFrom(clazz) }
            if (!settings.applyTestConfiguration(context.module, tasks, classes, createFilter)) {
                LOG.warn("Cannot apply class test configuration, uses raw run configuration")
                performRunnable.run()
            }
            settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(context.module)
            configuration.name = classes.joinToString("|") { it.name ?: "<error>" }
            performRunnable.run()
        }
    }
}

abstract class AbstractKotlinTestClassGradleConfigurationProducer
    : TestClassGradleConfigurationProducer(), KotlinGradleConfigurationProducer {
    override fun isConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
        if (!context.check()) {
            return false
        }

        if (!forceGradleRunner) {
            return super.isConfigurationFromContext(configuration, context)
        }

        if (GradleConstants.SYSTEM_ID != configuration.settings.externalSystemId) return false
        return doIsConfigurationFromContext(configuration, context)
    }

    override fun setupConfigurationFromContext(
        configuration: GradleRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        if (!context.check()) {
            return false
        }

        context.location?.psiElement?.parent.safeAs<KtNamedFunction>()?.let {
            if (KotlinMainFunctionDetector.getInstance().isMain(it)) return false
        }

        if (!forceGradleRunner) {
            return super.setupConfigurationFromContext(configuration, context, sourceElement)
        }

        if (GradleConstants.SYSTEM_ID != configuration.settings.externalSystemId) return false
        if (sourceElement.isNull) return false

        configuration.isScriptDebugEnabled = false
        return doSetupConfigurationFromContext(configuration, context, sourceElement)
    }

    private fun ConfigurationContext.check(): Boolean {
        return hasTestFramework && module != null && isApplicable(module)
    }

    override fun getPsiClassForLocation(contextLocation: Location<*>) = getTestClassForKotlinTest(contextLocation)

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return checkShouldReplace(self, other) || super.isPreferredConfiguration(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return checkShouldReplace(self, other) || super.shouldReplace(self, other)
    }

    private fun checkShouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        if (self.isProducedBy(javaClass) && other.isProducedBy(TestClassGradleConfigurationProducer::class.java)) {
            return true
        }

        return false
    }
}