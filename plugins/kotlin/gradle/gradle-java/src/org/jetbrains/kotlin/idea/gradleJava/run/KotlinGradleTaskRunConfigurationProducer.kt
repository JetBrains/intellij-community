// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil.equals
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.plugins.gradle.execution.GradleRunConfigurationProducer
import org.jetbrains.plugins.gradle.execution.GradleRunnerUtil
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class KotlinGradleTaskRunConfigurationProducer : GradleRunConfigurationProducer() {
    override fun setupConfigurationFromContext(
        configuration: GradleRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val module = context.module ?: return false
        val location = context.location ?: return false
        if (!isInGradleKotlinScript(location.psiElement)) return false

        val projectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false

        val taskToRun = findTaskNameAllowingEDT(location.psiElement) ?: return false
        configuration.settings.externalProjectPath = projectPath
        configuration.settings.taskNames = listOf(taskToRun)
        configuration.name = AbstractExternalSystemTaskConfigurationType.generateName(
            module.project, configuration.settings
        )
        return true
    }

    override fun isConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
        val module = context.module ?: return false
        val location = context.location ?: return false
        if (!isInGradleKotlinScript(location.psiElement)) return false

        val projectPath = GradleRunnerUtil.resolveProjectPath(module) ?: return false

        val settings = configuration.settings
        if (!equals(projectPath, settings.externalProjectPath)) return false

        val taskFromSettings = settings.taskNames.singleOrNull() ?: return false
        val taskName = findTaskNameAllowingEDT(location.psiElement) ?: return false
        return taskName == taskFromSettings
    }

    /**
     * There is a bug in RunConfigurationProducer (see IJPL-156448). When the Run button is pressed, code execution happens in EDT.
     * That's why analysis on EDT is temporarily allowed. Once it gets fixed, `allowAnalysisOnEdt` should be removed.
     */
    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun findTaskNameAllowingEDT(psiElement: PsiElement): String? =
        allowAnalysisOnEdt {
            findTaskNameAround(psiElement)
        }
}
