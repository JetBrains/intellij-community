// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.quickfix.CleanupFix
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class DeprecatedSymbolUsageFix(
    element: KtReferenceExpression,
    replaceWith: ReplaceWith
) : DeprecatedSymbolUsageFixBase(element, replaceWith), CleanupFix, HighPriorityAction {

    override fun getFamilyName() = KotlinBundle.message("replace.deprecated.symbol.usage")

    override fun getText() = KotlinBundle.message("replace.with.0", replaceWith.pattern)

    override fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?) {
        val element = element ?: return
        replacementStrategy.createReplacer(element)?.invoke()?.let { result ->
            val offset = (result.getCalleeExpressionIfAny() ?: result).textOffset
            editor?.moveCaret(offset)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val (referenceExpression, replacement) = extractDataFromDiagnostic(diagnostic, false) ?: return null
            return DeprecatedSymbolUsageFix(referenceExpression, replacement).takeIf(DeprecatedSymbolUsageFix::available)
        }

        fun importDirectivesToBeRemoved(file: KtFile): List<KtImportDirective> {
            if (file.hasAnnotationToSuppressDeprecation()) return emptyList()
            return file.importDirectives.filter { isImportToBeRemoved(it) }
        }

        private fun KtFile.hasAnnotationToSuppressDeprecation(): Boolean {
            val suppressAnnotationEntry = annotationEntries.firstOrNull {
                it.shortName?.asString() == "Suppress"
                        && it.resolveToCall()?.resultingDescriptor?.containingDeclaration?.fqNameSafe == StandardNames.FqNames.suppress
            } ?: return false
            return suppressAnnotationEntry.valueArguments.any {
                val text = (it.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.singleOrNull()?.text ?: return@any false
                text.equals("DEPRECATION", ignoreCase = true)
            }
        }

        private fun isImportToBeRemoved(import: KtImportDirective): Boolean {
            if (import.isAllUnder) return false

            val targetDescriptors = import.targetDescriptors()
            if (targetDescriptors.isEmpty()) return false

            return targetDescriptors.all {
                fetchReplaceWithPattern(it, import.project, null, false) != null
            }
        }
    }
}
