// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure

object StarProjectionUtils {
    @IntentionFamilyName
    val starProjectionFixFamilyName = KotlinBundle.message("fix.add.star.projection.family")

    @IntentionName
    fun addStarProjectionsActionName(argumentCount: Int): String {
        return KotlinBundle.message("fix.add.star.projection.text", getTypeNameAndStarProjectionsString("", argumentCount))
    }

    fun addStarProjections(project: Project, element: KtUserType, argumentCount: Int) {
        val typeString = getTypeNameAndStarProjectionsString(element.text, argumentCount)
        val psiFactory = KtPsiFactory(project)
        val replacement = psiFactory.createType(typeString).typeElement.sure { "No type element after parsing $typeString" }
        element.replace(replacement)
    }

    @IntentionName
    fun changeToStarProjectionActionName(element: KtTypeElement): String {
        val type = element.typeArgumentsAsTypes.joinToString { "*" }
        return KotlinBundle.message("fix.change.to.star.projection.text", "<$type>")
    }

    fun changeToStarProjection(project: Project, element: KtTypeElement) {
        val star = KtPsiFactory(project).createStar()
        element.typeArgumentsAsTypes.forEach { it?.replace(star) }
    }

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