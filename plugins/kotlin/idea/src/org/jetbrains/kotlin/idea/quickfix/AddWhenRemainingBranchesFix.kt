// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.cfg.*
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.quoteIfNeeded
import org.jetbrains.kotlin.idea.intentions.ImportAllMembersIntention
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

class AddWhenRemainingBranchesFix(
    expression: KtWhenExpression,
    val withImport: Boolean = false
) : KotlinQuickFixAction<KtWhenExpression>(expression) {

    override fun getFamilyName() = text

    override fun getText(): String {
        if (withImport) {
            return KotlinBundle.message("fix.add.remaining.branches.with.star.import")
        } else {
            return KotlinBundle.message("fix.add.remaining.branches")
        }
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        return isAvailable(element)
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        addRemainingBranches(element, withImport)
    }

    companion object : KotlinIntentionActionsFactory() {
        private fun KtWhenExpression.hasEnumSubject(): Boolean {
            val subject = subjectExpression ?: return false
            val descriptor = subject.analyze().getType(subject)?.constructor?.declarationDescriptor ?: return false
            return (descriptor as? ClassDescriptor)?.kind == ClassKind.ENUM_CLASS
        }

        override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
            val whenExpression = diagnostic.psiElement.getNonStrictParentOfType<KtWhenExpression>() ?: return emptyList()
            val actions = mutableListOf(AddWhenRemainingBranchesFix(whenExpression))
            if (whenExpression.hasEnumSubject()) {
                actions += AddWhenRemainingBranchesFix(whenExpression, withImport = true)
            }
            return actions
        }

        fun isAvailable(element: KtWhenExpression?): Boolean {
            if (element == null) return false
            return element.closeBrace != null &&
                    with(WhenChecker.getMissingCases(element, element.safeAnalyzeNonSourceRootCode())) { isNotEmpty() && !hasUnknown }
        }

        fun addRemainingBranches(element: KtWhenExpression?, withImport: Boolean = false) {
            if (element == null) return
            val missingCases = WhenChecker.getMissingCases(element, element.analyze())

            val whenCloseBrace = element.closeBrace ?: throw AssertionError("isAvailable should check if close brace exist")
            val elseBranch = element.entries.find { it.isElse }
            val psiFactory = KtPsiFactory(element)
            (whenCloseBrace.prevSibling as? PsiWhiteSpace)?.replace(psiFactory.createNewLine())
            for (case in missingCases) {
                val branchConditionText = when (case) {
                    WhenMissingCase.Unknown,
                    WhenMissingCase.NullIsMissing,
                    is WhenMissingCase.BooleanIsMissing,
                    is WhenMissingCase.ConditionTypeIsExpect -> case.branchConditionText
                    is WhenMissingCase.IsTypeCheckIsMissing ->
                        if (case.isSingleton) {
                            ""
                        } else {
                            "is "
                        } + case.classId.asSingleFqName().quoteIfNeeded().asString()
                    is WhenMissingCase.EnumCheckIsMissing -> case.callableId.asSingleFqName().quoteIfNeeded().asString()
                }
                val entry = psiFactory.createWhenEntry("$branchConditionText -> TODO()")
                if (elseBranch != null) {
                    element.addBefore(entry, elseBranch)
                } else {
                    element.addBefore(entry, whenCloseBrace)
                }
            }

            ShortenReferences.DEFAULT.process(element)

            if (withImport) {
                importAllEntries(element)
            }
        }

        private fun importAllEntries(element: KtWhenExpression) {
            with(ImportAllMembersIntention) {
                element.entries
                    .map { it.conditions.toList() }
                    .flatten()
                    .firstNotNullResult {
                        (it as? KtWhenConditionWithExpression)?.expression as? KtDotQualifiedExpression
                    }?.importReceiverMembers()
            }
        }
    }
}
