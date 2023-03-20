// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.actions.KotlinAddImportAction
import org.jetbrains.kotlin.idea.actions.createGroupedImportsAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.highlighter.Fe10QuickFixProvider
import org.jetbrains.kotlin.idea.quickfix.ImportFixBase
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
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

    private fun computeAutoImport(file: KtFile,
                                  editor: Editor,
                                  reference: KtSimpleNameExpression,
                                  startOffset: Int,
                                  endOffset: Int): KotlinAddImportAction? {
        if (hasUnresolvedImportWhichCanImport(file, reference.getReferencedName())) return null

        val bindingContext = reference.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        val diagnostics = bindingContext.diagnostics.filter {
            it.severity == Severity.ERROR && startOffset <= it.psiElement.startOffset && it.psiElement.endOffset <= endOffset
        }.ifEmpty { return null }

        val quickFixProvider = Fe10QuickFixProvider.getInstance(reference.project)

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
        }.firstNotNullOfOrNull { action ->
            val suggestions = filterSuggestions(file, action.suggestions())
            val singlePackage = suggestions.groupBy { it.parentOrNull() ?: FqName.ROOT }.size == 1
            if (!singlePackage) {
                null
            } else {
                val suggestion = suggestions.first()
                val descriptors = file.resolveImportReference(suggestion)

                // we do not auto-import nested classes because this will probably add qualification into the text and this will confuse the user
                if (descriptors.any { it is ClassDescriptor && it.containingDeclaration is ClassDescriptor }) {
                    null
                } else {
                    val element = action.element
                    if (element is KtElement) {
                        createGroupedImportsAction(file.project, editor, element, "", suggestions)
                    } else {
                        null
                    }
                }
            }
        }
    }

    override fun computeAutoImportAtOffset(editor: Editor, file: PsiFile, offset: Int, allowCaretNearReference: Boolean): BooleanSupplier? {
        if (file !is KtFile) return null

        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)
        val nameExpressions = file.elementsInRange(TextRange(startOffset, endOffset))
            .flatMap { it.collectDescendantsOfType<KtSimpleNameExpression>() }
        val action: KotlinAddImportAction = nameExpressions.firstNotNullOfOrNull { ref ->
            if (ref.endOffset != offset) computeAutoImport(file, editor, ref, startOffset, endOffset) else null
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
