// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.k2.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic0
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationBundle
import org.jetbrains.kotlin.idea.k2.refactoring.introduceProperty.KotlinIntroducePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.chooseContainer.chooseContainerElementIfNecessary
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHintByKey
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParentContainedIn
import org.jetbrains.kotlinx.serialization.compiler.fir.checkers.FirSerializationErrors

internal object JsonFormatRedundantFixFactory {

    val extractToProperty = KotlinQuickFixFactory.IntentionBased { diagnostic: KaCompilerPluginDiagnostic0 ->
        if (diagnostic.factoryName != FirSerializationErrors.JSON_FORMAT_REDUNDANT.name) return@IntentionBased emptyList()
        val element = (diagnostic.psi as? KtCallExpression) ?: return@IntentionBased emptyList()

        listOf(
            ExtractToPropertyQuickFix(element)
        )
    }

    private class ExtractToPropertyQuickFix(
        element: KtCallExpression,
    ) : KotlinQuickFixAction<KtCallExpression>(element) {

        override fun startInWriteAction(): Boolean = false
        override fun getText(): String = KotlinSerializationBundle.message("extract.json.to.property")
        override fun getFamilyName(): String = text

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            editor ?: return
            val element = this@ExtractToPropertyQuickFix.element ?: return

            selectContainer(project, editor, element) {
                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

                val outermostParent = element.getOutermostParentContainedIn(it)
                if (outermostParent == null) {
                    showErrorHintByKey(
                        project = project,
                        editor = editor,
                        messageKey = "cannot.refactor.no.container",
                        title = text,
                    )
                    return@selectContainer
                }
                KotlinIntroducePropertyHandler().doInvoke(project, editor, file, listOf(element), outermostParent)
            }
        }

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
            return IntentionPreviewInfo.EMPTY
        }

        private fun selectContainer(
            project: Project,
            editor: Editor,
            element: PsiElement,
            onSelect: (PsiElement) -> Unit,
        ) {
            val parent = element.parent ?: throw AssertionError("Must have at least one parent")

            val containers = parent.getExtractionContainers(
                strict = true,
                includeAll = true,
            ).filter {
                it is KtClassBody || (it is KtFile && !it.isScript())
            }

            if (containers.isEmpty()) {
                showErrorHintByKey(
                    project = project,
                    editor = editor,
                    messageKey = "cannot.refactor.no.container",
                    title = text,
                )
                return
            }

            chooseContainerElementIfNecessary(
                containers = containers,
                editor = editor,
                title = KotlinBundle.message("title.select.target.code.block"),
                highlightSelection = true,
                selection = null,
                toPsi = { it },
                onSelect = { onSelect(it) }
            )
        }
    }
}
