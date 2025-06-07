// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeCastFunction::class)

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object StarProjectionUtils {
    @IntentionFamilyName
    val starProjectionFixFamilyName = KotlinBundle.message("fix.add.star.projection.family")

    data class ChangeToStarProjectionFixInfo(
        val binaryExpr: KtBinaryExpressionWithTypeRHS?,
        val typeReference: KtTypeReference?,
        val typeElement: KtTypeElement,
    )

    fun getChangeToStarProjectionFixInfo(element: PsiElement): ChangeToStarProjectionFixInfo? {
        val binaryExpr = element.getNonStrictParentOfType<KtBinaryExpressionWithTypeRHS>()
        val typeReference = binaryExpr?.right ?: element.getNonStrictParentOfType()
        val typeElement = typeReference?.typeElement ?: return null
        return if (typeElement is KtFunctionType) null
        else ChangeToStarProjectionFixInfo(binaryExpr, typeReference, typeElement)
    }

    fun getUnwrappedType(element: KtTypeElement): KtUserType? {
        return generateSequence(element) { (it as? KtNullableType)?.innerType }.lastOrNull() as? KtUserType
    }

    fun getTypeNameAndStarProjectionsString(name: String, size: Int): String {
        val stars = CharArray(size) { '*' }
        val starProjections = stars.joinToString(prefix = "<", postfix = ">")
        return "$name$starProjections"
    }

    fun PsiElement.isOnJvm(): Boolean = safeAs<KtElement>()?.platform.isJvm()

    fun KtSimpleNameExpression.isAsKeyword(): Boolean {
        val elementType = getReferencedNameElementType()
        return elementType == KtTokens.AS_KEYWORD || elementType == KtTokens.AS_SAFE
    }
}