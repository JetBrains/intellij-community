// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

private const val START: String = "start"
private const val END_INCLUSIVE: String = "endInclusive"
private const val FIRST: String = "first"
private const val LAST: String = "last"

internal class ReplaceRangeStartEndInclusiveWithFirstLastInspection : KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.selectorExpression }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val selectorExpression = element.selectorExpression ?: return false
        return selectorExpression.text == START || selectorExpression.text == END_INCLUSIVE
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val receiverType = element.receiverExpression.expressionType as? KaClassType ?: return null
        val classSymbol = receiverType.symbol as? KaClassSymbol ?: return null
        return classSymbol.isRange().asUnit
    }

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Unit): String {
        val selectorExpression = element.selectorExpression ?: return ""
        return if (selectorExpression.text == START) {
            KotlinBundle.message("could.be.replaced.with.unboxed.first")
        } else {
            KotlinBundle.message("could.be.replaced.with.unboxed.last")
        }
    }

    override fun createQuickFix(element: KtDotQualifiedExpression, context: Unit): KotlinModCommandQuickFix<KtDotQualifiedExpression> {
        val selectorExpression = element.selectorExpression
        return if (selectorExpression == null || selectorExpression.text == START) {
            ReplaceIntRangeStartWithFirstQuickFix()
        } else {
            ReplaceIntRangeEndInclusiveWithLastQuickFix()
        }
    }
}

private class ReplaceIntRangeStartWithFirstQuickFix : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("replace.int.range.start.with.first.quick.fix.text")

    override fun applyFix(project: Project, element: KtDotQualifiedExpression, updater: ModPsiUpdater) {
        val selector = element.selectorExpression ?: return
        selector.replace(KtPsiFactory(project).createExpression(FIRST))
    }
}

private class ReplaceIntRangeEndInclusiveWithLastQuickFix : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
    override fun getFamilyName(): String = KotlinBundle.message("replace.int.range.end.inclusive.with.last.quick.fix.text")

    override fun applyFix(project: Project, element: KtDotQualifiedExpression, updater: ModPsiUpdater) {
        val selector = element.selectorExpression ?: return
        selector.replace(KtPsiFactory(project).createExpression(LAST))
    }
}

private fun KaClassSymbol.isRange(): Boolean {
    val fqName = classId?.asSingleFqName()?.asString() ?: return false
    return fqName in rangeTypes
}

private val rangeTypes: Set<String> = setOf(
    "kotlin.ranges.IntRange",
    "kotlin.ranges.CharRange",
    "kotlin.ranges.LongRange",
    "kotlin.ranges.UIntRange",
    "kotlin.ranges.ULongRange"
)
