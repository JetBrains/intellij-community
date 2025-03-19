// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression

object AddRemainingWhenBranchesUtils {
    class ElementContext(
        val whenMissingCases: List<WhenMissingCase>,
        val enumToStarImport: ClassId?,
    )

    fun familyAndActionName(useStarImport: Boolean): @IntentionFamilyName String =
        if (useStarImport) KotlinBundle.message("fix.add.remaining.branches.with.star.import")
        else KotlinBundle.message("fix.add.remaining.branches")

    fun addRemainingWhenBranches(
        whenExpression: KtWhenExpression,
        elementContext: ElementContext,
    ) {
        generateWhenBranches(whenExpression, elementContext.whenMissingCases)
        shortenReferences(
            whenExpression,
            callableShortenStrategy = {
                if (it.callableId?.classId == elementContext.enumToStarImport) {
                    ShortenStrategy.SHORTEN_AND_STAR_IMPORT
                } else {
                    ShortenStrategy.DO_NOT_SHORTEN
                }
            }
        )
    }

    fun generateWhenBranches(element: KtWhenExpression, missingCases: List<WhenMissingCase>) {
        val psiFactory = KtPsiFactory(element.project)
        val whenCloseBrace = element.closeBrace ?: run {
            val craftingMaterials = psiFactory.createExpression("when(1){}") as KtWhenExpression
            if (element.rightParenthesis == null) {
                element.addAfter(
                    craftingMaterials.rightParenthesis!!,
                    element.subjectExpression ?: throw AssertionError("caller should have checked the presence of subject expression.")
                )
            }
            if (element.openBrace == null) {
                element.addAfter(craftingMaterials.openBrace!!, element.rightParenthesis!!)
            }
            element.addAfter(craftingMaterials.closeBrace!!, element.entries.lastOrNull() ?: element.openBrace!!)
            element.closeBrace!!
        }
        val elseBranch = element.entries.find { it.isElse }
        (whenCloseBrace.prevSibling as? PsiWhiteSpace)?.replace(psiFactory.createNewLine())
        for (case in missingCases) {
            val branchConditionText = case.branchConditionText
            val entry = psiFactory.createWhenEntry("$branchConditionText -> TODO()")
            if (elseBranch != null) {
                element.addBefore(entry, elseBranch)
            } else {
                element.addBefore(entry, whenCloseBrace)
            }
        }
    }
}