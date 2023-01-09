// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cfg.WhenChecker
import org.jetbrains.kotlin.cfg.hasUnknown
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils.generateWhenBranches
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.ImportAllMembersIntention
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

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

            generateWhenBranches(element, missingCases)

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
                    .firstNotNullOfOrNull {
                        (it as? KtWhenConditionWithExpression)?.expression as? KtDotQualifiedExpression
                    }?.importReceiverMembers()
            }
        }
    }
}
