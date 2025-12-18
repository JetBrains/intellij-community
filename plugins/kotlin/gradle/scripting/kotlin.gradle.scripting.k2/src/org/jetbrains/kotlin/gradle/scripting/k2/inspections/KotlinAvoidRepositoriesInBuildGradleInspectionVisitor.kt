// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.PathUtil
import com.intellij.util.asSafely
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_REPOSITORY_HANDLER
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
import org.jetbrains.plugins.gradle.util.getGradleVersion
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists


private enum class RepositoriesParentBlockKind(val blockName: String) {
    DEPENDENCY("dependencyResolutionManagement"), PLUGIN("pluginManagement")
}

class KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (!expression.isGradleRepositoriesBlock()) return

        val repositoriesParentBlockKind = getRepositoriesParentBlockKind(expression)
        val gradleVersion = getGradleVersion(holder.project, holder.file.virtualFile) ?: GradleVersion.current()
        if (repositoriesParentBlockKind == RepositoriesParentBlockKind.DEPENDENCY && gradleVersion < GradleVersion.version("6.8")) return

        val settingsFile = expression.module?.getBuildScriptSettingsPsiFile()
        val fix = when (settingsFile) {
            null -> LocalQuickFix.from(CreateSettingsAndMoveRepositoriesAction(repositoriesParentBlockKind, gradleVersion))
            is KtFile -> MoveRepositoriesToSettingsFile(settingsFile, repositoriesParentBlockKind, gradleVersion)
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
        val symbol = resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return false
        val callableId = symbol.callableId ?: return false
        if (callableId.callableName.asString() != "repositories") return false
        if (callableId.packageName != FqName(GRADLE_KOTLIN_PACKAGE)) return false
        // check if the call parameter is of type `RepositoryHandler.() -> Unit`
        val params = symbol.valueParameters
        if (params.size != 1) return false
        val paramReturnType = params.single().returnType as? KaFunctionType ?: return false
        if (paramReturnType.classId.asSingleFqName() != FqName("kotlin.Function1")) return false
        val leftParam = paramReturnType.typeArguments.getOrNull(0)?.type as? KaClassType ?: return false
        val rightParam = paramReturnType.typeArguments.getOrNull(1)?.type as? KaClassType ?: return false
        if (leftParam.classId.asSingleFqName() != FqName(GRADLE_API_REPOSITORY_HANDLER)) return false
        if (rightParam.classId.asSingleFqName() != FqName("kotlin.Unit")) return false
        true
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
    private val repositoriesParentBlockKind: RepositoriesParentBlockKind,
    private val gradleVersion: GradleVersion
) : ModCommandAction {
    override fun getFamilyName(): @IntentionFamilyName String {
        return GradleInspectionBundle.message("intention.name.create.settings.and.move.repositories", repositoriesParentBlockKind.blockName)
    }

    override fun getPresentation(context: ActionContext): Presentation = Presentation.of(familyName)

    override fun perform(context: ActionContext): ModCommand {
        val element = context.element ?: return ModNothing()

        val settingsText = settingsScript(gradleVersion, GradleDsl.KOTLIN) {
            when (repositoriesParentBlockKind) {
                RepositoriesParentBlockKind.PLUGIN -> pluginManagement { code(element.text.normalizeBlockIndent().lines()) }

                RepositoriesParentBlockKind.DEPENDENCY -> addCode {
                    call(RepositoriesParentBlockKind.DEPENDENCY.blockName) {
                        if (supportsValReassignment(gradleVersion)) {
                            assign("repositoriesMode", code("RepositoriesMode.PREFER_PROJECT"))
                        } else {
                            code("repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)")
                        }
                        code(element.text.normalizeBlockIndent().lines())
                    }
                }
            }
        }

        val projectDir = context.project.guessProjectDir() ?: element.containingFile.virtualFile.parent
        val settingsFile = FutureVirtualFile(
            projectDir,
            KOTLIN_DSL_SETTINGS_FILE_NAME,
            FileTypeRegistry.getInstance().getFileTypeByExtension("kts")
        )

        return ModCreateFile(settingsFile, ModCreateFile.Text(settingsText))
            .andThen(ModCommand.psiUpdate(context) { updater -> updater.getWritable(element).delete() })
            .andThen(ModNavigate(settingsFile, 0, 0, 0))
    }

    private fun String.normalizeBlockIndent(): String {
        val lines = lines()
        if (lines.size <= 1) return this
        val withoutFirstLine = lines.drop(1).joinToString("\n").trimIndent()
        return lines.first() + "\n" + withoutFirstLine
    }
}

private class MoveRepositoriesToSettingsFile(
    settingsFile: KtFile,
    private val repositoriesParentBlockKind: RepositoriesParentBlockKind,
    private val gradleVersion: GradleVersion
) : KotlinModCommandQuickFix<KtCallExpression>() {
    private val settingsFilePointer = settingsFile.createSmartPointer()

    override fun getName(): @IntentionName String =
        GradleInspectionBundle.message("intention.name.move.repositories.to.settings.file", repositoriesParentBlockKind.blockName)

    override fun getFamilyName(): @IntentionFamilyName String =
        GradleInspectionBundle.message("intention.family.name.move.repositories.to.settings.file")

    override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
        val itemsToMove = element.getBlock()?.allChildren?.filterNot { it is PsiWhiteSpace }?.toList() ?: emptyList()
        val psiFactory = KtPsiFactory(element.project, true)
        val settingsFileCopy = updater.getWritable(settingsFilePointer.element!!)

        element.delete()

        val (repositoriesParentBlock, wasRepositoriesParentAdded) = when (repositoriesParentBlockKind) {
            RepositoriesParentBlockKind.PLUGIN -> settingsFileCopy.findOrAddPluginManagementBlock(psiFactory)
            RepositoriesParentBlockKind.DEPENDENCY -> settingsFileCopy.findOrAddDependencyResolutionManagementBlock(
                psiFactory,
                gradleVersion
            )
        }

        val (repositoriesBlock, wasRepositoriesAdded) = repositoriesParentBlock.findOrAddRepositoriesBlock(psiFactory)

        val movedItems = if (!repositoriesBlock.startsWith(itemsToMove)) {
            repositoriesBlock.appendDistinctSuffix(psiFactory, itemsToMove)
        } else {
            emptyList()
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

    private fun KtBlockExpression.startsWith(itemsToMove: List<PsiElement>): Boolean {
        val itemsToMoveTexts = itemsToMove.map { it.text }
        val existingItemsTexts = this.allChildren.filterNot { it is PsiWhiteSpace }.take(itemsToMoveTexts.size).map { it.text }.toList()
        return itemsToMoveTexts == existingItemsTexts
    }

    /**
     * The list of repositories to move may be the same as the existing list with an extra repository at the end.
     * In that case the overlapping part will not be re-added.
     */
    private fun KtBlockExpression.appendDistinctSuffix(psiFactory: KtPsiFactory, itemsToMove: List<PsiElement>): List<PsiElement> {
        val existingItems = this.allChildren.filterNot { it is PsiWhiteSpace }.toList()
        return calculateSuffixToAppend(itemsToMove, existingItems).map { itemToMove ->
            this.add(psiFactory.createNewLine())
            if (itemToMove is PsiComment) this.add(psiFactory.createComment(itemToMove.text))
            else this.add(itemToMove.copy())
        }
    }

    private fun calculateSuffixToAppend(itemsToMove: List<PsiElement>, existingItems: List<PsiElement>): List<PsiElement> {
        if (itemsToMove.isEmpty() || existingItems.isEmpty()) return itemsToMove

        // find the longest prefix of itemsToMove that matches the suffix of existingItems
        var overlapSize = 0
        val maxPossibleOverlap = minOf(itemsToMove.size, existingItems.size)
        val itemsToMoveTexts = itemsToMove.map { it.text }
        val existingItemsTexts = existingItems.map { it.text }

        for (i in 1..maxPossibleOverlap) {
            val itemsPrefix = itemsToMoveTexts.take(i)
            val currentSuffix = existingItemsTexts.takeLast(i)

            if (itemsPrefix == currentSuffix) {
                overlapSize = i
            }
        }

        // return the tail after overlap
        return itemsToMove.drop(overlapSize)
    }

    private fun KtFile.findOrAddPluginManagementBlock(psiFactory: KtPsiFactory): Pair<KtBlockExpression, Boolean> {
        val pluginManagementName = RepositoriesParentBlockKind.PLUGIN.blockName
        val existingBlock = this.findScriptInitializer(pluginManagementName)?.getBlock()
        if (existingBlock != null) return existingBlock to false
        return this.addAfter(psiFactory.createExpression("$pluginManagementName {\n}"), null)
            .apply { parent.addAfter(psiFactory.createNewLine(2), this) }
            .asSafely<KtCallExpression>()!!.getBlock()!! to true
    }

    private fun KtFile.findOrAddDependencyResolutionManagementBlock(
        psiFactory: KtPsiFactory,
        gradleVersion: GradleVersion
    ): Pair<KtBlockExpression, Boolean> {
        val dependencyResolutionManagementName = RepositoriesParentBlockKind.DEPENDENCY.blockName
        val existingBlock = this.findScriptInitializer(dependencyResolutionManagementName)?.getBlock()
        if (existingBlock != null) return existingBlock to false

        val anchor = this.findScriptInitializer("plugins")
            ?: this.findScriptInitializer(RepositoriesParentBlockKind.PLUGIN.blockName)
        val scriptBlock = this.script!!.blockExpression
        val repositoriesModeText =
            if (supportsValReassignment(gradleVersion)) "repositoriesMode = RepositoriesMode.PREFER_PROJECT"
            else "repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)"
        val dependencyResolutionManagementCall = psiFactory.createExpression(
            "$dependencyResolutionManagementName {\n$repositoriesModeText\n}"
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

private fun supportsValReassignment(gradleVersion: GradleVersion) = gradleVersion >= GradleVersion.version("8.2")
