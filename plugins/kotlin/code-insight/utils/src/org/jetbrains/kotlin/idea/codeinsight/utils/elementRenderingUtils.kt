// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtSuperTypeEntry
import org.jetbrains.kotlin.psi.KtSuperTypeList
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.utils.ifEmpty
import kotlin.math.min

fun KtElement.renderTrimmed(): String {
    class Renderer : KtTreeVisitorVoid() {
        val builder = StringBuilder()

        fun render(element: KtElement): String {
            element.accept(this)
            return builder.toString()
        }

        private fun <T : PsiElement> Iterable<T>.join(
            separator: CharSequence = ", ",
            prefix: CharSequence = "",
            postfix: CharSequence = ""
        ) {
            builder.append(prefix)
            for ((count, element) in withIndex()) {
                if (count > 0) builder.append(separator)
                element.accept(this@Renderer)
            }
            builder.append(postfix)
        }

        // Whitespace and comments

        override fun visitWhiteSpace(space: PsiWhiteSpace) {
            val text = space.text
            val newLine = text.indexOf('\n')
            if (newLine != 0) {
                builder.append(' ')
            }
            if (newLine >= 0) {
                builder.append(text.substring(newLine))
            }
        }

        override fun visitComment(comment: PsiComment) {

        }

        // Basic expressions

        override fun visitParenthesizedExpression(expression: KtParenthesizedExpression) {
            builder.append('(')
            expression.expression?.accept(this)
            builder.append(')')
        }

        override fun visitParameterList(list: KtParameterList) {
            list.parameters.join(prefix = "(", postfix = ")")
        }

        override fun visitParameter(parameter: KtParameter) {
            builder.append("${parameter.name}: ")
            parameter.typeReference?.accept(this)
        }

        override fun visitLabeledExpression(expression: KtLabeledExpression) {
            expression.baseExpression?.accept(this)
        }

        override fun visitAnnotatedExpression(expression: KtAnnotatedExpression) {
            expression.baseExpression?.accept(this)
        }

        override fun visitPrefixExpression(expression: KtPrefixExpression) {
            builder.append(expression.operationReference.getReferencedName())
            expression.baseExpression?.accept(this)
        }

        override fun visitPostfixExpression(expression: KtPostfixExpression) {
            expression.baseExpression?.accept(this)
            builder.append(expression.operationReference.getReferencedName())
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            expression.left?.accept(this)
            builder.append(" ${expression.operationReference.getReferencedName()} ")
            expression.right?.accept(this)
        }

        override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
            expression.left.accept(this)
            builder.append(" ${expression.operationReference.getReferencedName()} ")
            expression.right?.accept(this)
        }

        override fun visitIsExpression(expression: KtIsExpression) {
            expression.leftHandSide.accept(this)
            builder.append(" is ")
            expression.typeReference?.accept(this)
        }

        override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
            expression.arrayExpression?.accept(this)
            expression.indexExpressions.join(builder, prefix = "[", postfix = "]")
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            expression.calleeExpression?.accept(this)
            expression.typeArgumentList?.accept(this)
            expression.valueArgumentList?.accept(this)
            repeat(expression.lambdaArguments.size) { builder.append("{$ellipsis}") }
        }

        override fun visitValueArgumentList(list: KtValueArgumentList) {
            val arguments = list.arguments
            builder.append("(")
            if (arguments.isNotEmpty()) {
                if (arguments.size <= 3 &&
                    arguments.all { it.getArgumentExpression() is KtConstantExpression } &&
                    arguments.sumOf { it.text.length } < 8) {
                    arguments.joinTo(builder) { it.text }
                } else {
                    builder.append(ellipsis)
                }
            }
            builder.append(")")
        }

        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            expression.receiverExpression.accept(this)
            builder.append(expression.operationTokenNode.text)
            expression.selectorExpression?.accept(this)
        }

        override fun visitTypeReference(typeReference: KtTypeReference) {
            builder.append(typeReference.text)
        }

        override fun visitThisExpression(expression: KtThisExpression) {
            builder.append("this")
            expression.getLabelName()?.let { builder.append("@$it") }
        }

        override fun visitSuperExpression(expression: KtSuperExpression) {
            builder.append("super")
            expression.superTypeQualifier?.let {
                builder.append("<")
                it.accept(this)
                builder.append(">")
            }
            expression.getLabelName()?.let { builder.append("@$it") }
        }

        // Control structures

        override fun visitBreakExpression(expression: KtBreakExpression) {
            builder.append("break")
            expression.getLabelName()?.let { builder.append("@$it") }
        }

        override fun visitContinueExpression(expression: KtContinueExpression) {
            builder.append("continue")
            expression.getLabelName()?.let { builder.append("@$it") }
        }

        override fun visitThrowExpression(expression: KtThrowExpression) {
            builder.append("throw ")
            expression.thrownExpression?.accept(this)
        }

        override fun visitReturnExpression(expression: KtReturnExpression) {
            builder.append("return")
            expression.getLabelName()?.let { builder.append("@$it") }
            builder.append(' ')
            expression.returnedExpression?.accept(this)
        }

        override fun visitBlockExpression(expression: KtBlockExpression) {
            if (expression.parent is KtFunctionLiteral) {
                super.visitBlockExpression(expression)
            } else {
                builder.append("{$ellipsis}")
            }
        }

        override fun visitIfExpression(expression: KtIfExpression) {
            builder.append("if (")
            expression.condition?.accept(this)
            builder.append(")")
            expression.then?.let {
                builder.append(' ')
                it.accept(this)
            }
            expression.`else`?.let {
                builder.append(" else ")
                it.accept(this)
            }
        }

        override fun visitWhenExpression(expression: KtWhenExpression) {
            builder.append("when")
            expression.subjectExpression?.let {
                builder.append('(')
                it.accept(this)
                builder.append(')')
            }
            builder.append(" {$ellipsis}")
        }

        override fun visitForExpression(expression: KtForExpression) {
            builder.append("for (")
            (expression.loopParameter ?: expression.destructuringDeclaration)?.accept(this)
            builder.append(" in ")
            expression.loopRange?.accept(this)
            builder.append(")")
            expression.body?.let {
                builder.append(' ')
                it.accept(this)
            }
        }

        override fun visitWhileExpression(expression: KtWhileExpression) {
            builder.append("while (")
            expression.condition?.accept(this)
            builder.append(")")
            expression.body?.let {
                builder.append(' ')
                it.accept(this)
            }
        }

        override fun visitDoWhileExpression(expression: KtDoWhileExpression) {
            builder.append("do")
            expression.body?.let {
                builder.append(' ')
                it.accept(this)
            }
            builder.append(" while (")
            expression.condition?.accept(this)
            builder.append(")")
        }

        override fun visitTryExpression(expression: KtTryExpression) {
            builder.append("try {$ellipsis}")
        }

        // Declarations

        override fun visitNamedFunction(function: KtNamedFunction) {
            builder.append("fun")
            function.receiverTypeReference?.let {
                builder.append('.')
                it.accept(this)
            }
            function.name?.let { builder.append(" $it") }
            function.valueParameterList?.accept(this)
            function.equalsToken?.let { builder.append(" = ") }
            function.bodyExpression?.accept(this)
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            builder.append(if (accessor.isGetter) "get" else "set")
            builder.append("()")
            accessor.equalsToken?.let { builder.append(" = ") }
            accessor.bodyExpression?.accept(this)
        }

        override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
            constructor.valueParameterList?.accept(this)
        }

        override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
            builder.append("constructor")
            constructor.valueParameterList?.accept(this)
            constructor.bodyExpression?.accept(this)
        }

        override fun visitClassOrObject(classOrObject: KtClassOrObject) {
            val keyword = when (classOrObject) {
                is KtClass -> classOrObject.getClassOrInterfaceKeyword()
                is KtObjectDeclaration -> classOrObject.getObjectKeyword()
                else -> return
            }
            keyword?.accept(this)

            classOrObject.name?.let { builder.append(" $it") }
            classOrObject.getSuperTypeList()?.accept(this)
            classOrObject.body?.let { builder.append(" {$ellipsis}") }
        }

        override fun visitSuperTypeList(list: KtSuperTypeList) {
            list.entries.ifEmpty { return }.join(builder, prefix = " : ")
        }

        override fun visitDelegatedSuperTypeEntry(specifier: KtDelegatedSuperTypeEntry) {
            specifier.typeReference?.accept(this)
            specifier.delegateExpression?.let {
                builder.append(" by ")
                it.accept(this)
            }
        }

        override fun visitSuperTypeCallEntry(call: KtSuperTypeCallEntry) {
            call.typeReference?.accept(this)
            call.valueArgumentList?.accept(this)
        }

        override fun visitSuperTypeEntry(specifier: KtSuperTypeEntry) {
            specifier.typeReference?.accept(this)
        }

        // Default

        override fun visitElement(element: PsiElement) {
            if (element is LeafPsiElement) {
                builder.append(element.text)
            } else {
                super.visitElement(element)
            }
        }
    }

    return Renderer().render(this)
}

internal const val ellipsis = "${Typography.ellipsis}"

@NlsSafe
fun getExpressionShortText(element: PsiElement): String {
    val text = ((element as? KtElement)?.renderTrimmed() ?: element.text).trimStart()
    val firstNewLinePos = text.indexOf('\n')
    var trimmedText = text.substring(0, if (firstNewLinePos != -1) firstNewLinePos else min(100, text.length))
    if (trimmedText.length != text.length) trimmedText += " $ellipsis"
    return trimmedText
}