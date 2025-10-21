// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2ChangePackageDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.move.processor.K2ChangePackageRefactoringProcessor
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.hasIdentifiersOnly
import org.jetbrains.kotlin.idea.refactoring.introduce.showErrorHint
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtPackageDirective

private const val PACKAGE_NAME_VAR = "PACKAGE_NAME"

internal class ChangePackageIntention : SelfTargetingOffsetIndependentIntention<KtPackageDirective>(
    KtPackageDirective::class.java,
    KotlinBundle.messagePointer("intention.change.package.text")
) {

    override fun isApplicableTo(element: KtPackageDirective): Boolean = element.packageNameExpression != null

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        // Rename template only intention: no reasonable preview possible
        return IntentionPreviewInfo.EMPTY
    }

    override fun applyTo(element: KtPackageDirective, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")

        val file = element.containingKtFile
        val project = file.project

        val nameExpression = element.packageNameExpression!!
        val currentName = element.qualifiedName

        val builder = TemplateBuilderImpl(file)
        builder.replaceElement(
            nameExpression,
            PACKAGE_NAME_VAR,
            object : Expression() {
                override fun calculateQuickResult(context: ExpressionContext?) = TextResult(currentName)
                override fun calculateResult(context: ExpressionContext?) = TextResult(currentName)
                override fun calculateLookupItems(context: ExpressionContext?) = arrayOf(LookupElementBuilder.create(currentName))
            },
            true
        )

        var enteredName: String? = null
        var affectedRange: TextRange? = null

        editor.caretModel.moveToOffset(0)
        TemplateManager.getInstance(project).startTemplate(
            editor,
            builder.buildInlineTemplate(),
            object : TemplateEditingAdapter() {
                override fun beforeTemplateFinished(state: TemplateState, template: Template?) {
                    enteredName = state.getVariableValue(PACKAGE_NAME_VAR)!!.text
                    affectedRange = state.getSegmentRange(0)
                }

                override fun templateFinished(template: Template, brokenOff: Boolean) {
                    if (brokenOff) return
                    val name = enteredName ?: return
                    val range = affectedRange ?: return

                    // Restore original name and run refactoring

                    val document = editor.document
                    project.executeWriteCommand(text) {
                        document.replaceString(
                            range.startOffset,
                            range.endOffset,
                            FqName(currentName).quoteIfNeeded().asString()
                        )
                    }
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

                    if (!FqNameUnsafe(name).hasIdentifiersOnly()) {
                        showErrorHint(
                            project = project,
                            editor = editor,
                            message = KotlinBundle.message("text.0.is.not.valid.package.name", name),
                            title = KotlinBundle.message("intention.change.package.text"),
                        )
                        return
                    }

                    val descriptor = K2ChangePackageDescriptor(
                        project = project,
                        files = setOf(file),
                        target = FqName(name),
                        searchForText = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT,
                        searchInComments = KotlinCommonRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS,
                    )
                    K2ChangePackageRefactoringProcessor(descriptor).run()
                }
            }
        )
    }
}
