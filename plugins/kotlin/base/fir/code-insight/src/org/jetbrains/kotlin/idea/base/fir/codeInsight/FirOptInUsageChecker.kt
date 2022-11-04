// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.analysis.api.annotations.KtAnnotated as KtAnnotatedHighLevelApi


/**
 * Checks whether there's an element lexically above in the tree, annotated with @[OptIn]`(X::class)`, or a declaration
 * annotated with `@X` where [annotationClassId] is the [ClassId] of X.
 *
 * This is K2 implementation which is very similar to [org.jetbrains.kotlin.resolve.checkers.OptInUsageChecker.Companion.isOptInAllowed].
 * One difference is that check of [kotlin.SubclassOptInRequired] is not implemented here since it is not needed for current method usages.
 */
@ApiStatus.Internal
fun KtAnalysisSession.isOptInAllowed(psi: PsiElement, annotationClassId: ClassId, languageVersionSettings: LanguageVersionSettings): Boolean {
    if (annotationClassId.asFqNameString() in languageVersionSettings.getFlag(AnalysisFlags.optIn)) return true

    return psi.parentsWithSelf.any { element ->
        isDeclarationAnnotatedWith(element, annotationClassId) ||
                isElementAnnotatedWithOptIn(element, annotationClassId)
    }
}

private fun KtAnalysisSession.isDeclarationAnnotatedWith(element: PsiElement, annotationClassId: ClassId): Boolean {
    if (element !is KtDeclaration) return false
    return true == (element.getSymbol() as? KtAnnotatedHighLevelApi)?.hasAnnotation(annotationClassId)
}

/**
 * Checks whether [element] is annotated with @[OptIn]`(X1::class, X2::class, ..., X_N::class)`,
 * where some of `X1, X2, ..., X_N` is [annotationClassId].
 */
private fun KtAnalysisSession.isElementAnnotatedWithOptIn(element: PsiElement, annotationClassId: ClassId): Boolean {
    return element is KtAnnotated && element.annotationEntries.any { entry ->
        val ktType = entry.typeReference?.getKtType()
        if (true == ktType?.isClassTypeWithClassId(OptInNames.OPT_IN_CLASS_ID)) {
            entry.valueArguments.any { valueArgument ->
                val expression = valueArgument.getArgumentExpression()
                expression != null && isClassLiteralExpressionOfClass(expression, annotationClassId)
            }
        } else false
    }
}

private fun KtAnalysisSession.isClassLiteralExpressionOfClass(expression: KtExpression, classId: ClassId): Boolean {
    val receiverExpression = (expression as? KtClassLiteralExpression)?.receiverExpression as? KtNameReferenceExpression
    return true == receiverExpression?.getKtType()?.isClassTypeWithClassId(classId)
}
