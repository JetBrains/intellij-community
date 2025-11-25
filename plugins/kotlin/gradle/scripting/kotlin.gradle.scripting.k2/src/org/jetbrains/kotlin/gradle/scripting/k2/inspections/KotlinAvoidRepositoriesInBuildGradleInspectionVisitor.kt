// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.*
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
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
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists


private enum class RepositoriesParentBlockKind(val blockName: String) {
    DEPENDENCY("dependencyResolutionManagement"), PLUGIN("pluginManagement")
}

class KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (!expression.isGradleRepositoriesBlock()) return

        val settingsFile = expression.module?.getBuildScriptSettingsPsiFile()
        val repositoriesParentBlockKind = getRepositoriesParentBlockKind(expression)

        val fix = when (settingsFile) {
            null -> LocalQuickFix.from(CreateSettingsAndMoveRepositoriesAction(repositoriesParentBlockKind))
            is KtFile -> MoveRepositoriesToSettingsFile(settingsFile, repositoriesParentBlockKind)
            else -> null
        }

        holder.problem(
            expression,
            GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor")
        ).range(expression.calleeExpression?.textRangeInParent ?: expression.textRangeInParent)
            .maybeFix(fix)
            .register()
    }

    private fun KtCallExpression.isGradleRepositoriesBlock(): Boolean = analyze(this) {
        val callableId = resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId
        return@analyze callableId?.callableName?.asString() == "repositories" &&
                callableId.packageName == FqName(GRADLE_KOTLIN_PACKAGE)
    }

    private fun getRepositoriesParentBlockKind(expression: KtCallExpression): RepositoriesParentBlockKind {
        return if (expression.findParentBlock("buildscript") != null) RepositoriesParentBlockKind.PLUGIN
        else RepositoriesParentBlockKind.DEPENDENCY
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

    private fun findSettingsFile(path: String): Path? {
        val root = Path(path)
        return setOf("settings.gradle.kts", "settings.gradle")
            .map { root.resolve(it) }
            .firstOrNull(Path::exists)
    }
}

private class CreateSettingsAndMoveRepositoriesAction(
    private val repositoriesParentBlockKind: RepositoriesParentBlockKind
) : ModCommandAction {
    override fun getFamilyName(): @IntentionFamilyName String {
        return GradleInspectionBundle.message("intention.name.create.settings.and.move.repositories", repositoriesParentBlockKind.blockName)
    }

    override fun getPresentation(context: ActionContext): Presentation = Presentation.of(familyName)

    override fun perform(context: ActionContext): ModCommand {
        val element = context.element ?: return ModNothing()

        val settingsText = settingsScript(GradleUtil.getGradleVersion(context.project, element.containingFile), GradleDsl.KOTLIN) {
            withFoojayPlugin()
            setProjectName(context.project.name)
            when (repositoriesParentBlockKind) {
                RepositoriesParentBlockKind.PLUGIN -> pluginManagement { code(element.text) }

                RepositoriesParentBlockKind.DEPENDENCY -> addCode {
                    call(RepositoriesParentBlockKind.DEPENDENCY.blockName) {
                        assign("repositoriesMode", code("RepositoriesMode.PREFER_PROJECT"))
                        code(element.text.lines())
                    }
                }
            }
        }

        val projectDir = context.project.guessProjectDir() ?: element.containingFile.virtualFile.parent
        val settingsFile = FutureVirtualFile(
            projectDir,
            "settings.gradle.kts",
            FileTypeRegistry.getInstance().getFileTypeByExtension("kts")
        )

        return ModCreateFile(settingsFile, ModCreateFile.Text(settingsText))
            .andThen(ModCommand.psiUpdate(context) { updater -> updater.getWritable(element).delete() })
            .andThen(ModNavigate(settingsFile, 0, 0, 0))
    }
}

private class MoveRepositoriesToSettingsFile(
    settingsFile: KtFile,
    private val repositoriesParentBlockKind: RepositoriesParentBlockKind
) : KotlinModCommandQuickFix<KtCallExpression>() {
    private val settingsFilePointer = settingsFile.createSmartPointer()

    override fun getName(): @IntentionName String =
        GradleInspectionBundle.message("intention.name.move.repositories.to.settings.file", repositoriesParentBlockKind.blockName)

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.move.repositories.to.settings.file")

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val itemsToMove = element.getBlock()?.children?.toList() ?: emptyList() // still want to move the block itself
        val psiFactory = KtPsiFactory(element.project, true)
        val settingsFileCopy = updater.getWritable(settingsFilePointer.element!!)

        element.delete()

        val (repositoriesParentBlock, wasRepositoriesParentAdded) = when (repositoriesParentBlockKind) {
            RepositoriesParentBlockKind.PLUGIN -> settingsFileCopy.findOrAddPluginManagementBlock(psiFactory)
            RepositoriesParentBlockKind.DEPENDENCY -> settingsFileCopy.findOrAddDependencyResolutionManagementBlock(psiFactory)
        }

        val (repositoriesBlock, wasRepositoriesAdded) = repositoriesParentBlock.findOrAddRepositoriesBlock(psiFactory)
        val existingItems = repositoriesBlock.statements.toList()

        // append the repositories to the end of the list
        val movedItems = calculateRepositoriesToAppend(itemsToMove, existingItems).map {
            repositoriesBlock.add(psiFactory.createNewLine())
            repositoriesBlock.add(it.copy())
        }

        // move the caret to the last added item
        updater.moveCaretTo(repositoriesBlock.children.lastOrNull() ?: repositoriesBlock)
        // highlight added lines if any
        if (wasRepositoriesParentAdded) updater.highlight(repositoriesParentBlock.parentOfType<KtCallExpression>()!!)
        else if (wasRepositoriesAdded) updater.highlight(repositoriesBlock.parentOfType<KtCallExpression>()!!)
        else if (movedItems.isNotEmpty()) updater.highlight(
            TextRange(movedItems.first().textRange.startOffset, movedItems.last().textRange.endOffset),
            EditorColors.SEARCH_RESULT_ATTRIBUTES
        )
    }

    /**
     * Checks if itemsToMove is a prefix of existingItems. If so, there is no need to move them.
     * Else returns the non-overlapping tail of itemsToMove.
     */
    private fun calculateRepositoriesToAppend(itemsToMove: List<PsiElement>, existingItems: List<KtExpression>): List<PsiElement> {
        val itemsToMoveTexts = itemsToMove.map { it.text }
        val existingItemsTexts = existingItems.take(itemsToMove.size).map { it.text }

        if (itemsToMoveTexts == existingItemsTexts) return emptyList()
        else return getRepositoriesTailAfterOverlap(itemsToMove, existingItems)
    }

    private fun getRepositoriesTailAfterOverlap(itemsToMove: List<PsiElement>, existingItems: List<KtExpression>): List<PsiElement> {
        val itemsToMoveStatements = itemsToMove.filterIsInstance<KtExpression>() // filter out comments, etc.
        if (itemsToMoveStatements.isEmpty() || existingItems.isEmpty()) return itemsToMove

        // Find the longest prefix of itemsToMove that matches the suffix of currentItems
        var overlapSize = 0
        val maxPossibleOverlap = minOf(itemsToMoveStatements.size, existingItems.size)

        for (i in 1..maxPossibleOverlap) {
            val itemsPrefix = itemsToMoveStatements.take(i).map { it.text }
            val currentSuffix = existingItems.takeLast(i).map { it.text }

            if (itemsPrefix == currentSuffix) {
                overlapSize = i
            }
        }

        // return the tail after overlap, without skipping comments
        val result = mutableListOf<PsiElement>()
        var skippedStatements = 0
        for (item in itemsToMove) {
            if (item is KtExpression) {
                if (skippedStatements < overlapSize) {
                    skippedStatements++
                    continue
                }
            }
            result.add(item)
        }
        return result
    }

    private fun KtFile.findOrAddPluginManagementBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val pluginManagementName = RepositoriesParentBlockKind.PLUGIN.blockName
        val existingBlock = this.findScriptInitializer(pluginManagementName)?.getBlock()
        if (existingBlock != null) return existingBlock to false
        return this.addAfter(psiFactory.createExpression("$pluginManagementName {\n}"), null)
            .apply { parent.addAfter(psiFactory.createNewLine(2), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!! to true
    }

    private fun KtFile.findOrAddDependencyResolutionManagementBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val dependencyResolutionManagementName = RepositoriesParentBlockKind.DEPENDENCY.blockName
        val existingBlock = this.findScriptInitializer(dependencyResolutionManagementName)?.getBlock()
        if (existingBlock != null) return existingBlock to false

        val anchor = this.findScriptInitializer("plugins")
            ?: this.findScriptInitializer(RepositoriesParentBlockKind.PLUGIN.blockName)
        val scriptBlock = this.script!!.blockExpression
        val dependencyResolutionManagementCall = psiFactory.createExpression(
            "$dependencyResolutionManagementName {\nrepositoriesMode = RepositoriesMode.PREFER_PROJECT\n}"
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