// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.match

class BeforeResolveHighlightingVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder) {
    override fun visitElement(element: PsiElement) {
        val elementType = element.node.elementType
        val attributes = when {
            element is KDocLink && !willApplyRainbowHighlight(element) -> KotlinNameHighlightInfoTypes.KDOC_LINK

            elementType in KtTokens.SOFT_KEYWORDS -> {
                when (elementType) {
                    in KtTokens.MODIFIER_KEYWORDS -> KotlinNameHighlightInfoTypes.BUILTIN_ANNOTATION
                    else -> KotlinNameHighlightInfoTypes.KEYWORD
                }
            }
            elementType == KtTokens.SAFE_ACCESS -> KotlinNameHighlightInfoTypes.SAFE_ACCESS
            elementType == KtTokens.EXCLEXCL -> KotlinNameHighlightInfoTypes.EXCLEXCL
            else -> return
        }

        highlightName(element, attributes)
    }

    private fun willApplyRainbowHighlight(element: KDocLink): Boolean {
        if (!RainbowHighlighter.isRainbowEnabledWithInheritance(EditorColorsManager.getInstance().globalScheme, KotlinLanguage.INSTANCE)) {
            return false
        }
        // Can't use resolve because it will access indices
        return (element.parent as? KDocTag)?.knownTag == KDocKnownTag.PARAM
    }

    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
        if (isUnitTestMode()) return

        val functionLiteral = lambdaExpression.functionLiteral
        highlightName(functionLiteral.lBrace, KotlinNameHighlightInfoTypes.FUNCTION_LITERAL_BRACES_AND_ARROW)

        val closingBrace = functionLiteral.rBrace
        if (closingBrace != null) {
            highlightName(closingBrace, KotlinNameHighlightInfoTypes.FUNCTION_LITERAL_BRACES_AND_ARROW)
        }

        val arrow = functionLiteral.arrow
        if (arrow != null) {
            highlightName(arrow, KotlinNameHighlightInfoTypes.FUNCTION_LITERAL_BRACES_AND_ARROW)
        }
    }

    override fun visitArgument(argument: KtValueArgument) {
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        highlightName(argument.project,
            TextRange(argumentName.startOffset, eq.endOffset),
            if (argument.parents.match(KtValueArgumentList::class, last = KtAnnotationEntry::class) != null)
                KotlinNameHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
            else
                KotlinNameHighlightInfoTypes.NAMED_ARGUMENT
        )
    }

    override fun visitExpressionWithLabel(expression: KtExpressionWithLabel) {
        val targetLabel = expression.getTargetLabel()
        if (targetLabel != null) {
            highlightName(targetLabel, KotlinNameHighlightInfoTypes.LABEL)
        }
    }

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        val calleeExpression = call.calleeExpression
        val typeElement = calleeExpression.typeReference?.typeElement
        if (typeElement is KtUserType) {
            typeElement.referenceExpression?.let { highlightName(it, KotlinNameHighlightInfoTypes.CONSTRUCTOR_CALL) }
        }
        super.visitSuperTypeCallEntry(call)
    }


    override fun visitTypeParameter(parameter: KtTypeParameter) {
        parameter.nameIdentifier?.let { highlightName(it, KotlinNameHighlightInfoTypes.TYPE_PARAMETER) }
        super.visitTypeParameter(parameter)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        highlightNamedDeclaration(function, KotlinNameHighlightInfoTypes.FUNCTION_DECLARATION)
        super.visitNamedFunction(function)
    }
}
