// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.components.ShortenStrategy
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspection.EnumValuesSoftDeprecateInspectionBase
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal class EnumValuesSoftDeprecateInspection : EnumValuesSoftDeprecateInspectionBase() {

    /**
     * Checks whether there's an element lexically above in the tree, annotated with @[OptIn]`(X::class)`, or a declaration
     * annotated with `@X` where [annotationClassId] is the [ClassId] of X.
     *
     * This is K2 implementation which is very similar to [org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed].
     * One difference is that check of [kotlin.SubclassOptInRequired] is not implemented here since it is not needed for current method usages.
     */
    context(KtAnalysisSession)
    override fun isOptInAllowed(element: KtCallExpression, annotationClassId: ClassId): Boolean {
        if (annotationClassId.asFqNameString() in element.languageVersionSettings.getFlag(AnalysisFlags.optIn)) return true

        return element.parentsWithSelf.any {
            isDeclarationAnnotatedWith(it, annotationClassId) ||
                    isElementAnnotatedWithOptIn(it, annotationClassId)
        }
    }

    override fun createQuickFix(fixType: ReplaceFixType, enumClassQualifiedName: String): LocalQuickFix {
        return K2ReplaceFix(fixType, enumClassQualifiedName)
    }

    private class K2ReplaceFix(fixType: ReplaceFixType, enumClassQualifiedName: String) :
        ReplaceFix(fixType, enumClassQualifiedName) {
        override fun shortenReferences(element: KtElement) {
            shortenReferences(element, callableShortenStrategy = { ShortenStrategy.SHORTEN_IF_ALREADY_IMPORTED })
        }
    }

    context(KtAnalysisSession)
    private fun isDeclarationAnnotatedWith(element: PsiElement, annotationClassId: ClassId): Boolean {
        if (element !is KtDeclaration) return false
        return true == (element.getSymbol() as? KtAnnotated)?.hasAnnotation(annotationClassId)
    }

    /**
     * Checks whether [element] is annotated with @[OptIn]`(X1::class, X2::class, ..., X_N::class)`,
     * where some of `X1, X2, ..., X_N` is [annotationClassId].
     */
    context(KtAnalysisSession)
    private fun isElementAnnotatedWithOptIn(element: PsiElement, annotationClassId: ClassId): Boolean {
        return element is org.jetbrains.kotlin.psi.KtAnnotated && element.annotationEntries.any { entry ->
            val ktType = entry.typeReference?.getKtType()
            if (true == ktType?.isClassTypeWithClassId(OptInNames.OPT_IN_CLASS_ID)) {
                entry.valueArguments.any { valueArgument ->
                    val expression = valueArgument.getArgumentExpression()
                    expression != null && isClassLiteralExpressionOfClass(expression, annotationClassId)
                }
            } else false
        }
    }

    context(KtAnalysisSession)
    private fun isClassLiteralExpressionOfClass(expression: KtExpression, classId: ClassId): Boolean {
        val receiverExpression = (expression as? KtClassLiteralExpression)?.receiverExpression as? KtNameReferenceExpression
        return true == receiverExpression?.getKtType()?.isClassTypeWithClassId(classId)
    }
}