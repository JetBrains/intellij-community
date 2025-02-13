// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.run.gradle

import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.ConfigurationFromContextImpl
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.gradle.AbstractKotlinGradleCodeInsightBaseTest
import org.jetbrains.kotlin.idea.gradleJava.run.findTaskNameAround
import org.jetbrains.kotlin.idea.gradleJava.run.isInGradleKotlinScript
import org.jetbrains.kotlin.idea.test.util.elementByOffset
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class AbstractKotlinGradleRunConfigurationProducerBaseTest : AbstractKotlinGradleCodeInsightBaseTest() {

    protected fun assertNoConfigurationAtCaret() {
        runInEdtAndWait {
            val context = getConfigurationContextAtCaret()
            assertTrue("There should be no configuration at caret"){
                context.configurationsFromContext.isNullOrEmpty()
            }
        }
    }

    protected fun verifyGradleConfigurationAtCaret(taskName: String) {
        runInEdtAndWait {
            val context = getConfigurationContextAtCaret()
            val contextDetails = getConfigurationContextDetails(context)
            val configurationFromContext = context.configurationsFromContext?.singleOrNull()
                ?: error("Unable to find a single configuration from context.\n$contextDetails")
            verifyGradleRunConfiguration(configurationFromContext, taskName)
            verifyConfigurationProducer(configurationFromContext, context)
        }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun getConfigurationContextDetails(context: ConfigurationContext): String {
        val module = context.module
        val location = context.location
        val psiElement = location?.psiElement
        val callExpression = psiElement?.getParentOfType<KtCallExpression>(false, KtScriptInitializer::class.java)
        val resolvedCall = callExpression?.let {
            allowAnalysisOnEdt {
                analyze(callExpression) {
                    callExpression.resolveToCall()
                }
            }
        }
        val functionCall = callExpression?.let {
            allowAnalysisOnEdt {
                analyze(callExpression) {
                    callExpression.resolveToCall()?.singleFunctionCallOrNull()
                }
            }
        }
        val taskName = psiElement?.let {
            allowAnalysisOnEdt {
                findTaskNameAround(it)
            }
        }
        return """
            |Configuration context details:
            |  Module: $module
            |  Location: $location
            |  Kotlin: ${psiElement?.let { isInGradleKotlinScript(it) }}
            |  Project path: ${module?.let { GradleRunnerUtil.resolveProjectPath(it) }}
            |  Task: $taskName
            |   - Resolved call: $resolvedCall
            |   - Function call: $functionCall
        """.trimMargin()
    }

    private fun getConfigurationContextAtCaret(): ConfigurationContext {
        codeInsightFixture.configureFromExistingVirtualFile(getFile("build.gradle.kts"))
        val location = PsiLocation(codeInsightFixture.elementByOffset)
        val context = ConfigurationContext.createEmptyContextForLocation(location)
        return context
    }

    private fun verifyGradleRunConfiguration(configurationFromContext: ConfigurationFromContext, taskName: String) {
        val gradleConfiguration = assertInstanceOf<GradleRunConfiguration>(configurationFromContext.configuration)
        assertEquals(listOf(taskName), gradleConfiguration.settings.taskNames,
                     "GradleRunConfiguration must contain only expected task name")
        assertEquals("${project.name} [$taskName]", gradleConfiguration.name)
    }

    private fun verifyConfigurationProducer(
        configurationFromContext: ConfigurationFromContext,
        context: ConfigurationContext,
    ) {
        val producer = configurationFromContext.safeAs<ConfigurationFromContextImpl>()?.configurationProducer
                       ?: error("Unable to find a RunConfigurationProducer for configuration")
        assertTrue(producer.isConfigurationFromContext(configurationFromContext.configuration, context),
                   "Producer must be able to identify a configuration that was created by it")
    }
}
