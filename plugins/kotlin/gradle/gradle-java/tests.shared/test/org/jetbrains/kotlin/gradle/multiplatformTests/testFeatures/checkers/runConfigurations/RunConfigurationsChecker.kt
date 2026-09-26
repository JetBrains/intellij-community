// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations

import com.intellij.execution.PsiLocation
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext.createEmptyContextForLocation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfiguration
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.ModuleReportData
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.PrinterContext
import org.jetbrains.kotlin.gradle.multiplatformTests.workspace.WorkspaceModelChecker
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

object RunConfigurationsChecker : WorkspaceModelChecker<RunConfigurationsCheckConfiguration>(true) {
    override val classifier: String = "run-configurations"

    override fun createDefaultConfiguration(): RunConfigurationsCheckConfiguration {
        return RunConfigurationsCheckConfiguration().apply {
            includeDetail("name") { configuration.name }
            includeDetail("type") { configuration.type.displayName }
            includeDetail("tasks") { (configuration as? GradleRunConfiguration)?.settings?.taskNames }
            includeDetail("scriptParameters") { (configuration as? GradleRunConfiguration)?.settings?.scriptParameters }
            includeDetail("isDebugAllEnabled") { (configuration as? GradleRunConfiguration)?.isDebugAllEnabled }
            includeDetail("isRunAsTest") { (configuration as? GradleRunConfiguration)?.isRunAsTest }
        }
    }

    override fun renderTestConfigurationDescription(testConfiguration: TestConfiguration): List<String> {
        return testConfiguration.getConfiguration(RunConfigurationsChecker).details.map { detail ->
            "Showing runConfiguration detail: ${detail.name}"
        }
    }

    override fun PrinterContext.buildReportDataForModule(module: Module): List<ModuleReportData> = runReadAction {
        val psiManager = PsiManager.getInstance(module.project)
        val allKotlinFiles = ModuleRootManager.getInstance(module).contentEntries.flatMap { it.sourceFolders.asList() }
            .mapNotNull { it.file }
            .withClosure<VirtualFile> { it.children.toList() }
            .filter { it.extension == "kt" }

        val allPsiElements = allKotlinFiles.flatMap { kotlinFile ->
            val psiFile = psiManager.findFile(kotlinFile) ?: return@flatMap emptySet()
            psiFile.withClosure<PsiElement> { it.children.toList() }
        }

        val allProducedConfigurations = allPsiElements.mapNotNull { psiElement ->
            PsiAndConfiguration(
                psiElement, createEmptyContextForLocation(PsiLocation(psiElement)).configuration ?: return@mapNotNull null
            )
        }.distinctBy { it.configuration.uniqueID }
            .sortedBy { it.configuration.uniqueID }

        allProducedConfigurations.flatMap { producedConfiguration ->
            val fqName = ModuleReportData("fqName: ${producedConfiguration.psi.kotlinFqName}")
            val details = testConfiguration.getConfiguration(RunConfigurationsChecker).details.mapNotNull { detail ->
                val value = detail.value(producedConfiguration.configuration) ?: return@mapNotNull null
                ModuleReportData("${detail.name}: $value")
            }
            listOf(fqName) + details + ModuleReportData("")
        }
    }

    private data class PsiAndConfiguration(val psi: PsiElement, val configuration: RunnerAndConfigurationSettings)
}