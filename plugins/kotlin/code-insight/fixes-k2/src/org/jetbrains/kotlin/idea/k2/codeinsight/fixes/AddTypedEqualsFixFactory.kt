// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.types.type
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.addMemberDeclarationAfter
import org.jetbrains.kotlin.idea.base.psi.removeModifierKeyword
import org.jetbrains.kotlin.idea.base.psi.setCallableTypeReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.withQualifiedDenotableSupertypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.render
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Adds the typed `equals(other: C)` to an inline value class and makes `equals(other: Any?)` call it.
 *
 * The typed function gets the body of `equals(other: Any?)`. A check of the parameter against `C` is always true there, so the fix
 * removes it.
 */
internal object AddTypedEqualsFixFactory {
    val inefficientEqualsOverridingInValueClassFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InefficientEqualsOverridingInValueClass ->
            val function = diagnostic.psi
            val parameter = function.valueParameters.singleOrNull() ?: return@ModCommandBased emptyList()
            if (parameter.nameIdentifier == null || parameter.typeReference == null) return@ModCommandBased emptyList()
            if (!function.hasBody() || function.containingClassOrObject !is KtClass) return@ModCommandBased emptyList()
            if (function.callsEqualsOnThis()) return@ModCommandBased emptyList()
            if (function.anyDescendantOfType<KtWhenConditionIsPattern>()) return@ModCommandBased emptyList()

            val typeChecks = collectTypeChecks(function, parameter.symbol, diagnostic.type) ?: return@ModCommandBased emptyList()
            val typeText = KaRenderer.withQualifiedDenotableSupertypes.renderToString(diagnostic.type)
            listOf(AddTypedEqualsFix(function, ElementContext(typeText, typeChecks)))
        }

    private fun KtNamedFunction.callsEqualsOnThis(): Boolean =
        anyDescendantOfType<KtCallExpression> { call ->
            if (call.calleeExpression?.text != OperatorNameConventions.EQUALS.asString()) return@anyDescendantOfType false
            val qualified = call.getQualifiedExpressionForSelector() as? KtQualifiedExpression ?: return@anyDescendantOfType true
            qualified.receiverExpression is KtThisExpression
        }

    /**
     * Returns the `is`, `!is` and `as` checks of [parameter] against [classType] in [function], with the ranges relative to [function].
     * Returns `null` when [function] checks [parameter] against another type, because that check does not compile on the typed parameter.
     */
    context(_: KaSession)
    private fun collectTypeChecks(function: KtNamedFunction, parameter: KaSymbol, classType: KaType): List<TypeCheck>? {
        val functionStart = function.textRange.startOffset
        val checks = mutableListOf<TypeCheck>()

        fun add(expression: KtExpression, checked: KtExpression?, typeReference: KtTypeReference?, kind: TypeCheckKind): Boolean {
            if (!checked.isReferenceTo(parameter)) return true
            if (typeReference?.type?.semanticallyEquals(classType) != true) return false
            checks += TypeCheck(expression.textRange.shiftLeft(functionStart), kind)
            return true
        }

        for (isExpression in function.collectDescendantsOfType<KtIsExpression>()) {
            val kind = if (isExpression.isNegated) TypeCheckKind.NOT_IS else TypeCheckKind.IS
            if (!add(isExpression, isExpression.leftHandSide, isExpression.typeReference, kind)) return null
        }
        for (cast in function.collectDescendantsOfType<KtBinaryExpressionWithTypeRHS>()) {
            val operation = cast.operationReference.getReferencedNameElementType()
            if (operation != KtTokens.AS_KEYWORD && operation != KtTokens.AS_SAFE) continue
            if (!add(cast, cast.left, cast.right, TypeCheckKind.AS)) return null
        }
        return checks
    }

    context(_: KaSession)
    private fun KtExpression?.isReferenceTo(symbol: KaSymbol): Boolean {
        val reference = KtPsiUtil.safeDeparenthesize(this ?: return false) as? KtNameReferenceExpression ?: return false
        return reference.mainReference.resolveToSymbol() == symbol
    }

    private enum class TypeCheckKind { IS, NOT_IS, AS }

    private data class TypeCheck(val range: TextRange, val kind: TypeCheckKind)

    private data class ElementContext(val typeText: String, val typeChecks: List<TypeCheck>)

    private class AddTypedEqualsFix(
        element: KtNamedFunction,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtNamedFunction, ElementContext>(element, elementContext) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.add.typed.equals.family")

        override fun getActionPresentation(context: ActionContext, element: KtNamedFunction): Presentation {
            val parameterName = element.valueParameters.singleOrNull()?.nameAsSafeName?.render() ?: return Presentation.of(familyName)
            val typeText = getElementContext(context, element).typeText.substringAfterLast('.')
            return Presentation.of(KotlinBundle.message("fix.add.typed.equals.text", "$parameterName: $typeText"))
        }

        override fun invoke(
            actionContext: ActionContext,
            element: KtNamedFunction,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val klass = element.containingClassOrObject as? KtClass ?: return
            val parameterName = element.valueParameters.singleOrNull()?.nameAsSafeName?.render() ?: return
            val psiFactory = KtPsiFactory(actionContext.project)

            val typedEquals = element.copy() as KtNamedFunction
            removeTypeChecks(typedEquals, elementContext.typeChecks, psiFactory)
            simplifyBooleanConstants(typedEquals)
            typedEquals.docComment?.delete()
            typedEquals.removeModifierKeyword(KtTokens.OVERRIDE_KEYWORD)
            val typedParameter = typedEquals.valueParameters.singleOrNull() ?: return
            typedParameter.setCallableTypeReference(psiFactory.createType(elementContext.typeText))

            val bridge = psiFactory.createFunction(
                "fun equals($parameterName: Any?): Boolean {\n" +
                        "if ($parameterName !is ${elementContext.typeText}) return false\n" +
                        "return equals($parameterName)\n" +
                        "}"
            )
            replaceBody(element, bridge, psiFactory)

            val addedTypedEquals = klass.addMemberDeclarationAfter(typedEquals, element)
            shortenReferences(element)
            shortenReferences(addedTypedEquals)
        }

        private fun removeTypeChecks(function: KtNamedFunction, typeChecks: List<TypeCheck>, psiFactory: KtPsiFactory) {
            val functionStart = function.textRange.startOffset
            val expressions = function.collectDescendantsOfType<KtExpression>()
            val targets = typeChecks.mapNotNull { check ->
                val expression = expressions.firstOrNull { it.textRange.shiftLeft(functionStart) == check.range } ?: return@mapNotNull null
                expression to check.kind
            }
            for ((expression, kind) in targets.sortedByDescending { it.first.textRange.startOffset }) {
                when (kind) {
                    TypeCheckKind.IS -> expression.replace(psiFactory.createExpression("true"))
                    TypeCheckKind.NOT_IS -> expression.replace(psiFactory.createExpression("false"))
                    TypeCheckKind.AS -> {
                        val operand = (expression as? KtBinaryExpressionWithTypeRHS)?.left ?: continue
                        val cast = expression.parent as? KtParenthesizedExpression ?: expression
                        val elvis = (cast.parent as? KtBinaryExpression)?.takeIf { it.operationToken == KtTokens.ELVIS && it.left == cast }
                        val replaced = elvis ?: cast
                        if (replaced.parent is KtBlockExpression) replaced.delete() else replaced.replace(operand)
                    }
                }
            }
        }

        private fun simplifyBooleanConstants(function: KtNamedFunction) {
            while (true) {
                val simplified = function.collectDescendantsOfType<KtExpression>().asReversed().any { simplify(it) }
                if (!simplified) return
            }
        }

        private fun simplify(expression: KtExpression): Boolean {
            when (expression) {
                is KtParenthesizedExpression -> {
                    val inner = expression.expression ?: return false
                    if (inner.booleanConstant() == null) return false
                    expression.replace(inner)
                    return true
                }

                is KtBinaryExpression -> {
                    val left = expression.left ?: return false
                    val right = expression.right ?: return false
                    val result = when (expression.operationToken) {
                        KtTokens.ANDAND -> when {
                            left.booleanConstant() == true -> right
                            right.booleanConstant() == true -> left
                            left.booleanConstant() == false -> left
                            else -> null
                        }

                        KtTokens.OROR -> when {
                            left.booleanConstant() == false -> right
                            right.booleanConstant() == false -> left
                            left.booleanConstant() == true -> left
                            else -> null
                        }

                        else -> null
                    } ?: return false
                    expression.replace(result)
                    return true
                }

                is KtIfExpression -> {
                    val condition = expression.condition?.booleanConstant() ?: return false
                    val branch = if (condition) expression.then else expression.`else`
                    val block = expression.parent as? KtBlockExpression
                    when {
                        branch == null && block != null -> expression.delete()
                        branch == null -> return false
                        branch is KtBlockExpression && block != null -> {
                            for (statement in branch.statements) {
                                block.addBefore(statement, expression)
                                block.addBefore(KtPsiFactory(expression.project).createNewLine(), expression)
                            }
                            expression.delete()
                        }

                        else -> expression.replace(branch)
                    }
                    return true
                }

                else -> return false
            }
        }

        private fun KtExpression.booleanConstant(): Boolean? {
            val constant = KtPsiUtil.safeDeparenthesize(this) as? KtConstantExpression ?: return null
            return when (constant.text) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

        private fun replaceBody(function: KtNamedFunction, bridge: KtNamedFunction, psiFactory: KtPsiFactory) {
            val bridgeBody = bridge.bodyBlockExpression ?: return
            function.bodyBlockExpression?.let { body ->
                body.replace(bridgeBody)
                return
            }

            if (function.typeReference == null) {
                function.setCallableTypeReference(psiFactory.createType("kotlin.Boolean"))
            }
            val typeReference = function.typeReference ?: return
            function.deleteChildRange(typeReference.nextSibling ?: return, function.lastChild)
            function.addAfter(bridgeBody, typeReference)
            function.addAfter(psiFactory.createWhiteSpace(), typeReference)
        }
    }
}
