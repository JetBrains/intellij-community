// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import java.util.function.BooleanSupplier

class KotlinReferenceImporter : AbstractKotlinReferenceImporter() {
    override fun isEnabledFor(file: KtFile): Boolean = KotlinCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly

    override val enableAutoImportFilter: Boolean = false
}

abstract class AbstractKotlinReferenceImporter : ReferenceImporter {
    override fun isAddUnambiguousImportsOnTheFlyEnabled(file: PsiFile): Boolean = file is KtFile && isEnabledFor(file)

    protected abstract fun isEnabledFor(file: KtFile): Boolean

    protected abstract val enableAutoImportFilter: Boolean

    private fun filterSuggestions(context: KtFile, suggestions: Collection<FqName>): Collection<FqName> =
        if (enableAutoImportFilter) {
            KotlinAutoImportsFilter.filterSuggestionsIfApplicable(context, suggestions)
        } else {
            suggestions
        }

    private fun computeAutoImport(file: KtFile, editor: Editor, expression: KtExpression): QuestionAction? {
        val quickFixes = KotlinReferenceImporterFacility.getInstance().createImportFixesForExpression(expression)

        return quickFixes.firstNotNullOfOrNull { importFix ->
            importFix.createAutoImportAction(editor, file) { suggestions -> filterSuggestions(file, suggestions) }
        }
    }

    override fun computeAutoImportAtOffset(editor: Editor, file: PsiFile, offset: Int, allowCaretNearReference: Boolean): BooleanSupplier? {
        if (file !is KtFile) return null

        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)
        val expressions = file.elementsInRange(TextRange(startOffset, endOffset))
            .flatMap { it.collectDescendantsOfType<KtExpression>() }
        val action: QuestionAction = expressions.firstNotNullOfOrNull { expression ->
            if (expression.endOffset != offset) computeAutoImport(file, editor, expression) else null
        } ?: return null

        return BooleanSupplier {
            doImport(action)
        }
    }

    private fun doImport(action: QuestionAction): Boolean {
        var res = false
        CommandProcessor.getInstance().runUndoTransparentAction {
            res = action.execute()
        }
        return res
    }
}
