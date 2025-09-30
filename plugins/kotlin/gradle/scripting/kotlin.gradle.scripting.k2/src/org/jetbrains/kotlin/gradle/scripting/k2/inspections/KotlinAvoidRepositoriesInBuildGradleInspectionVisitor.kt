// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.TextRange
import com.intellij.psi.createSmartPointer
import com.intellij.util.PathUtil
import com.intellij.psi.util.parentOfType
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

class KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val callableId = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId ?: return
            if (callableId.callableName.asString() != "repositories") return
            if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return
        }
        // A project without a settings file does not need to centralize its repositories
        val settingsFile = expression.module?.getBuildScriptSettingsPsiFile() ?: return
        val isForPlugins = expression.findParentBlock("buildscript") != null

        if (settingsFile is KtFile) {
            holder.problem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor")
            ).range(expression.calleeExpression?.textRangeInParent ?: expression.textRangeInParent)
                .fix(MoveRepositoriesToSettingsFile(settingsFile, isForPlugins))
                .register()
        } else {
            // Kotlin build script and Groovy settings script case
            holder.problem(
                expression,
                GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor")
            ).range(expression.calleeExpression?.textRangeInParent ?: expression.textRangeInParent)
                .register()
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
    settingsFile: KtFile, private val isForPlugins: Boolean
) : KotlinModCommandQuickFix<KtCallExpression>() {
    private val settingsFilePointer = settingsFile.createSmartPointer()

    override fun getName(): @IntentionName String {
        val repositoriesParentBlockName = when (isForPlugins) {
            true -> "pluginManagement"
            false -> "dependencyResolutionManagement"
        }
        return GradleInspectionBundle.message("intention.name.move.repositories.to.settings.file", repositoriesParentBlockName)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.move.repositories.to.settings.file")

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val itemsToMove = element.getBlock()?.statements?.toList() ?: emptyList() // still want to move the block itself
        val psiFactory = KtPsiFactory(element.project, true)
        val settingsFileCopy = updater.getWritable(settingsFilePointer.element!!)

        element.delete()

        val (repositoriesParentBlock, wasRepositoriesParentAdded) =
            if (isForPlugins) settingsFileCopy.findOrAddPluginManagementBlock(psiFactory)
            else settingsFileCopy.findOrAddDependencyResolutionManagementBlock(psiFactory)

        val (repositoriesBlock, wasRepositoriesAdded) = repositoriesParentBlock.findOrAddRepositoriesBlock(psiFactory)

        val existingItems = repositoriesBlock.statements.toList()

        // append the repositories to the end of the list
        val movedItems = getRepositoriesTailAfterOverlap(itemsToMove, existingItems).map {
            repositoriesBlock.add(psiFactory.createNewLine())
            repositoriesBlock.add(it.copy())
        }

        // move the caret to the last added item
        updater.moveCaretTo(repositoriesBlock.statements.lastOrNull() ?: repositoriesBlock)
        // highlight added lines if any
        if (wasRepositoriesParentAdded) updater.highlight(repositoriesParentBlock.parentOfType<KtCallExpression>()!!)
        else if (wasRepositoriesAdded) updater.highlight(repositoriesBlock.parentOfType<KtCallExpression>()!!)
        else if (movedItems.isNotEmpty()) updater.highlight(
            TextRange(movedItems.first().textRange.startOffset, movedItems.last().textRange.endOffset),
            EditorColors.SEARCH_RESULT_ATTRIBUTES
        )
    }

    // returns the non-overlapping tail of itemsToMove
    private fun getRepositoriesTailAfterOverlap(itemsToMove: List<KtExpression>, existingItems: List<KtExpression>): List<KtExpression> {
        if (itemsToMove.isEmpty() || existingItems.isEmpty()) return itemsToMove

        // Find the longest prefix of itemsToMove that matches the suffix of currentItems
        var overlapSize = 0
        val maxPossibleOverlap = minOf(itemsToMove.size, existingItems.size)

        for (i in 1..maxPossibleOverlap) {
            val itemsPrefix = itemsToMove.take(i).map { it.text }
            val currentSuffix = existingItems.takeLast(i).map { it.text }

            if (itemsPrefix == currentSuffix) {
                overlapSize = i
            }
        }

        // Return the tail after overlap
        return itemsToMove.drop(overlapSize)
    }

    private fun KtFile.findOrAddPluginManagementBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val existingBlock = this.findScriptInitializer("pluginManagement")?.getBlock()
        if (existingBlock != null) return existingBlock to false
        return this.addAfter(psiFactory.createExpression("pluginManagement {\n}"), null)
            .apply { parent.addAfter(psiFactory.createNewLine(2), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!! to true
    }


    private fun KtFile.findOrAddDependencyResolutionManagementBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val existingBlock = this.findScriptInitializer("dependencyResolutionManagement")?.getBlock()
        if (existingBlock != null) return existingBlock to false

        val anchor = this.findScriptInitializer("plugins")
            ?: this.findScriptInitializer("pluginManagement")
        val scriptBlock = this.script!!.blockExpression
        val dependencyResolutionManagementCall = psiFactory.createExpression(
            "dependencyResolutionManagement {\nrepositoriesMode = RepositoriesMode.PREFER_PROJECT\n}"
        )

        return scriptBlock.addAfter(dependencyResolutionManagementCall, anchor)
            .apply { scriptBlock.addBefore(psiFactory.createNewLine(2), this) }
            .apply { if (anchor == null && this.nextSibling != null) scriptBlock.addAfter(psiFactory.createNewLine(2), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!! to true
    }

    private fun KtBlockExpression.findOrAddRepositoriesBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val existingBlock = this.findBlock("repositories")
        if (existingBlock != null) return existingBlock to false
        return this.add(psiFactory.createExpression("repositories {\n}"))
            .apply { parent.addBefore(psiFactory.createNewLine(), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!! to true
    }
}