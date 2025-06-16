// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class BeforeResolveHighlightingVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder), DumbAware {
    override fun visitElement(element: PsiElement) {
        val elementType = element.node.elementType
        val attributes = when {
            element is KDocLink && !willApplyRainbowHighlight(element) -> KotlinHighlightInfoTypeSemanticNames.KDOC_LINK

            elementType in KtTokens.SOFT_KEYWORDS -> {
                when (elementType) {
                    in KtTokens.MODIFIER_KEYWORDS -> KotlinHighlightInfoTypeSemanticNames.BUILTIN_ANNOTATION
                    else -> KotlinHighlightInfoTypeSemanticNames.KEYWORD
                }
            }
            elementType == KtTokens.ALL_KEYWORD -> KotlinHighlightInfoTypeSemanticNames.KEYWORD // workaround can be removed after KT-77099
            elementType == KtTokens.SAFE_ACCESS -> KotlinHighlightInfoTypeSemanticNames.SAFE_ACCESS
            elementType == KtTokens.EXCLEXCL -> KotlinHighlightInfoTypeSemanticNames.EXCLEXCL
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
        if (ApplicationManager.getApplication().isUnitTestMode()) return

        val functionLiteral = lambdaExpression.functionLiteral
        highlightName(functionLiteral.lBrace, KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_BRACES_AND_ARROW)

        val closingBrace = functionLiteral.rBrace
        if (closingBrace != null) {
            highlightName(closingBrace, KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_BRACES_AND_ARROW)
        }

        val arrow = functionLiteral.arrow
        if (arrow != null) {
            highlightName(arrow, KotlinHighlightInfoTypeSemanticNames.FUNCTION_LITERAL_BRACES_AND_ARROW)
        }
    }

    override fun visitArgument(argument: KtValueArgument) {
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        val parent = argument.parent
        highlightName(argument.project,
                      argument,
            TextRange(argumentName.startOffset, eq.endOffset),
            if (parent is KtValueArgumentList && parent.parent is KtAnnotationEntry)
                KotlinHighlightInfoTypeSemanticNames.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
            else
                KotlinHighlightInfoTypeSemanticNames.NAMED_ARGUMENT
        )
    }

    override fun visitExpressionWithLabel(expression: KtExpressionWithLabel) {
        val targetLabel = expression.getTargetLabel()
        if (targetLabel != null) {
            highlightName(targetLabel, KotlinHighlightInfoTypeSemanticNames.LABEL)
        }
    }

    override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
        val calleeExpression = call.calleeExpression
        val typeElement = calleeExpression.typeReference?.typeElement
        if (typeElement is KtUserType) {
            typeElement.referenceExpression?.let { highlightName(it, KotlinHighlightInfoTypeSemanticNames.CONSTRUCTOR_CALL) }
        }
        super.visitSuperTypeCallEntry(call)
    }


    override fun visitTypeParameter(parameter: KtTypeParameter) {
        parameter.nameIdentifier?.let { highlightName(it, KotlinHighlightInfoTypeSemanticNames.TYPE_PARAMETER) }
        super.visitTypeParameter(parameter)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        highlightNamedDeclaration(function, KotlinHighlightInfoTypeSemanticNames.FUNCTION_DECLARATION)
        super.visitNamedFunction(function)
    }
}

class BeforeResolveHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor =
        BeforeResolveHighlightingVisitor(holder)
}