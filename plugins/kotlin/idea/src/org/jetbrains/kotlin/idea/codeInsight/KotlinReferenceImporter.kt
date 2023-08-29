// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.actions.KotlinAddImportAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.quickfix.ImportFixBase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
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

    private fun hasUnresolvedImportWhichCanImport(file: KtFile, name: String): Boolean = file.importDirectives.any {
        (it.isAllUnder || it.importPath?.importedName?.asString() == name) && it.targetDescriptors().isEmpty()
    }

    private fun computeAutoImport(file: KtFile, editor: Editor, expression: KtExpression): KotlinAddImportAction? {
        val referencedName = (expression as? KtSimpleNameExpression)?.getReferencedName()
        if (referencedName != null && hasUnresolvedImportWhichCanImport(file, referencedName)) return null

        val bindingContext = expression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        val diagnostics = bindingContext.diagnostics.filter {
            it.severity == Severity.ERROR && expression.textRange in it.psiElement.textRange
        }.ifEmpty { return null }

        val quickFixProvider = Fe10QuickFixProvider.getInstance(expression.project)

        return sequence {
            diagnostics.groupBy { it.psiElement }.forEach { (_, sameElementDiagnostics) ->
                sameElementDiagnostics.groupBy { it.factory }.forEach { (_, sameTypeDiagnostic) ->
                    val quickFixes = quickFixProvider.createUnresolvedReferenceQuickFixes(sameTypeDiagnostic)
                    for (action in quickFixes.values()) {
                        if (action is ImportFixBase<*>) {
                            this.yield(action)
                        }
                    }
                }
            }
        }.firstNotNullOfOrNull { importFix ->
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
        val action: KotlinAddImportAction = expressions.firstNotNullOfOrNull { expression ->
            if (expression.endOffset != offset) computeAutoImport(file, editor, expression) else null
        } ?: return null

        return BooleanSupplier {
            doImport(action)
        }
    }

    private fun doImport(action: KotlinAddImportAction): Boolean {
        var res = false
        CommandProcessor.getInstance().runUndoTransparentAction {
            res = action.execute()
        }
        return res
    }
}
