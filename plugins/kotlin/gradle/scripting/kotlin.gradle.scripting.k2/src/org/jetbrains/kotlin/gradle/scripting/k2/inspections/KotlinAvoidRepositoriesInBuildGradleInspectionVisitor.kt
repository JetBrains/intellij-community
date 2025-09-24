// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

class KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return
            if (symbol.callableId?.callableName?.asString() != "repositories") return
            if (symbol.callableId?.packageName != FqName("org.gradle.kotlin.dsl")) return
        }
        // A project without a settings file does not need to centralize its repositories
        val settingsFile = expression.module?.getBuildScriptSettingsPsiFile() ?: return

        if (settingsFile is KtFile) {
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor"),
                MoveRepositoriesToSettingsFile(settingsFile.createSmartPointer())
            )
        } else {
            // Kotlin build script and Groovy settings script case
            holder.registerProblem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor")
            )
        }
    }

    private fun Module.getBuildScriptSettingsPsiFile(): PsiFile? =
        ExternalSystemApiUtil.getExternalProjectPath(this)?.let { externalProjectPath ->
            generateSequence(externalProjectPath) {
                PathUtil.getParentPath(it).ifBlank { null }
            }.mapNotNull {
                findSettingsFile(it)
            }.map { settingsPath ->
                VfsUtil.findFile(settingsPath, true)?.findPsiFile(project)
            }.firstOrNull()
        }

    private fun findSettingsFile(path: String): Path? = setOf("settings.gradle.kts", "settings.gradle")
        .map { Path("$path/$it") }
        .firstOrNull(Path::exists)
}

private class MoveRepositoriesToSettingsFile(
    val settingsFile: SmartPsiElementPointer<KtFile>
) : KotlinModCommandQuickFix<KtCallExpression>() {
    override fun getName(): @IntentionName String = GradleInspectionBundle.message("intention.name.move.repositories.to.settings.file")

    override fun getFamilyName(): @IntentionFamilyName String = name

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val isForPlugins = element.findParentBlock("buildscript") != null
        val itemsToMove = element.getBlock()?.statements?.toList() ?: emptyList() // still want to move the block itself
        val psiFactory = KtPsiFactory(element.project, true)
        val settingsFileCopy = updater.getWritable(settingsFile.element!!)

        val parentBlock =
            if (isForPlugins) settingsFileCopy.findOrAddPluginManagementBlock(psiFactory)
            else settingsFileCopy.findOrAddDependencyResolutionManagementBlock(psiFactory)

        val repositoriesBlock = parentBlock.findOrAddRepositoriesBlock(psiFactory)

        val lastStatement = itemsToMove.map {
            repositoriesBlock.add(psiFactory.createNewLine())
            repositoriesBlock.add(it)
        }.last()

        element.delete()
        updater.moveCaretTo(lastStatement)
    }

    private fun KtFile.findOrAddPluginManagementBlock(psiFactory: KtPsiFactory): KtBlockExpression =
        this.findScriptInitializer("pluginManagement")?.getBlock()
            ?: this.addAfter(psiFactory.createExpression("pluginManagement {\n}"), null)
                .apply { parent.addAfter(psiFactory.createNewLine(2), this) }
                .asSafely<KtCallExpression>()!!.getBlock()!!


    private fun KtFile.findOrAddDependencyResolutionManagementBlock(psiFactory: KtPsiFactory): KtBlockExpression {
        val existingBlock = this.findScriptInitializer("dependencyResolutionManagement")?.getBlock()
        if (existingBlock != null) return existingBlock

        val anchor = this.findScriptInitializer("plugins")
            ?: this.findScriptInitializer("pluginManagement")
        val scriptBlock = this.script!!.blockExpression
        val dependencyResolutionManagementCall = psiFactory.createExpression(
            "dependencyResolutionManagement {\nrepositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS\n}"
        )

        return scriptBlock.addAfter(dependencyResolutionManagementCall, anchor)
            .apply { scriptBlock.addBefore(psiFactory.createNewLine(2), this) }
            .apply { if (anchor == null && this.nextSibling != null) scriptBlock.addAfter(psiFactory.createNewLine(2), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!!
    }

    private fun KtBlockExpression.findOrAddRepositoriesBlock(psiFactory: KtPsiFactory) =
        this.findBlock("repositories")
            ?: this.add(psiFactory.createExpression("repositories {\n}"))
                .apply { parent.addBefore(psiFactory.createNewLine(), this) }
                .asSafely<KtCallExpression>()!!.getBlock()!!
}