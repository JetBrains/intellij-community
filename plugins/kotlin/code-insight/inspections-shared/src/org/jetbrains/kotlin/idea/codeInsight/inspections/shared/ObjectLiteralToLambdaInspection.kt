// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.*
import org.jetbrains.kotlin.idea.base.psi.replaceSamConstructorCall
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.ReturnSaver
import org.jetbrains.kotlin.idea.util.application.runWriteActionIfPhysical
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.types.Variance

@Suppress("DEPRECATION")
internal class ObjectLiteralToLambdaInspection : IntentionBasedInspection<KtObjectLiteralExpression>(ObjectLiteralToLambdaIntention::class) {
    override fun problemHighlightType(element: KtObjectLiteralExpression): ProblemHighlightType {
        val data = extractData(element) ?: return super.problemHighlightType(element)
        val bodyBlock = data.singleFunction.bodyBlockExpression
        val lastStatement = bodyBlock?.statements?.lastOrNull()

        if (bodyBlock?.anyDescendantOfType<KtReturnExpression> { it != lastStatement } == true)
            return ProblemHighlightType.INFORMATION

        val valueArgument = element.parent as? KtValueArgument
        valueArgument?.getStrictParentOfType<KtCallExpression>()?.let { call ->
            val classId = analyze(call) {
                val functionCallOrNull = call.resolveToCall()?.successfulFunctionCallOrNull()
                val argumentExpression = valueArgument.getArgumentExpression()
                val variableSignature = functionCallOrNull?.argumentMapping?.get(argumentExpression)
                val returnType = variableSignature?.returnType?.withNullability(KaTypeNullability.NON_NULLABLE) as? KaClassType
                returnType?.classId
            }
            if (classId != data.baseTypeClassId) return ProblemHighlightType.INFORMATION
        }

        return super.problemHighlightType(element)
    }
}

class ObjectLiteralToLambdaIntention : SelfTargetingRangeIntention<KtObjectLiteralExpression>(
    KtObjectLiteralExpression::class.java,
    KotlinBundle.messagePointer("convert.to.lambda"),
    KotlinBundle.messagePointer("convert.object.literal.to.lambda")
) {
    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun applicabilityRange(element: KtObjectLiteralExpression): TextRange? {
        val data = extractData(element) ?: return null

        val singleFunction = data.singleFunction

        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(singleFunction) {
                    var callableSymbol = singleFunction.symbol as? KaCallableSymbol ?: return null
                    val allOverriddenSymbol = callableSymbol.directlyOverriddenSymbols.singleOrNull() ?: return null
                    if (allOverriddenSymbol.modality != KaSymbolModality.ABSTRACT) return null
                }
            }
        }

        if (!singleFunction.hasBody()) return null
        if (singleFunction.valueParameters.any { it.name == null }) return null

        val bodyExpression = singleFunction.bodyExpression!!

        // this-reference
        if (bodyExpression.anyDescendantOfType<KtThisExpression> { thisReference ->
                val instanceReference = thisReference.instanceReference
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        analyze(instanceReference) {
                            var containingSymbol = singleFunction.symbol.containingSymbol ?: return@analyze false
                            val resolveToSymbol = instanceReference.mainReference.resolveToSymbol()

                            resolveToSymbol.equalsOrEqualsByPsi(containingSymbol)
                        }
                    }
                }
            }
        ) return null

        // Recursive call, skip labels
        if (ReferencesSearch.search(singleFunction, LocalSearchScope(bodyExpression)).asIterable().
            any { it.element !is KtLabelReferenceExpression }) {
            return null
        }

        if (bodyExpression.anyDescendantOfType<KtExpression> { expression ->
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        analyze(expression) {
                            var containingSymbol = singleFunction.symbol.containingSymbol ?: return@analyze false
                            val functionCall = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze false
                            functionCall.getImplicitReceivers().any {
                                it.symbol == containingSymbol
                            }
                        }
                    }
                }
            }
        ) {
            return null
        }

        val objectKeyword = element.objectDeclaration.getObjectKeyword()!!
        return objectKeyword.textRange.union(data.baseTypeRef.textRange)
    }

    @OptIn(KaIdeApi::class, KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
    override fun applyTo(element: KtObjectLiteralExpression, editor: Editor?) {
        val data = extractData(element) ?: return
        val singleFunction = data.singleFunction

        val commentSaver = CommentSaver(element)
        val returnSaver = ReturnSaver(singleFunction)

        val body = singleFunction.bodyExpression!!

        val psiFactory = KtPsiFactory(element.project)
        val newExpression = psiFactory.buildExpression {
            appendFixedText(data.typeRepresentation)

            appendFixedText("{")

            val parameters = singleFunction.valueParameters

            val needParameters =
                parameters.count() > 1 || parameters.any { parameter -> ReferencesSearch.search(parameter, LocalSearchScope(body)).asIterable().any() }
            if (needParameters) {
                parameters.forEachIndexed { index, parameter ->
                    if (index > 0) {
                        appendFixedText(",")
                    }
                    appendName(parameter.nameAsSafeName)
                }

                appendFixedText("->")
            }

            val lastCommentOwner = if (singleFunction.hasBlockBody()) {
                val contentRange = (body as KtBlockExpression).contentRange()
                appendChildRange(contentRange)
                contentRange.last
            } else {
                appendExpression(body)
                body
            }

            if (lastCommentOwner?.anyDescendantOfType<PsiComment> { it.tokenType == KtTokens.EOL_COMMENT } == true) {
                appendFixedText("\n")
            }
            appendFixedText("}")
        }

        val replaced = runWriteActionIfPhysical(element) { element.replaced(newExpression) }
        val pointerToReplaced = replaced.createSmartPointer()
        val callee = replaced.callee
        val callExpression = callee.parent as KtCallExpression
        val functionLiteral = callExpression.lambdaArguments.single().getLambdaExpression()!!

        val returnLabel = callee.getReferencedNameAsName()
        runWriteActionIfPhysical(element) {
            returnSaver.restore(functionLiteral, returnLabel)
        }
        val parentCall = ((replaced.parent as? KtValueArgument)
            ?.parent as? KtValueArgumentList)
            ?.parent as? KtCallExpression

        val singleOrNull = parentCall?.let {
            allowAnalysisOnEdt {
                allowAnalysisFromWriteAction {
                    analyze(it) {
                        samConstructorCallsToBeConverted(it).singleOrNull()
                    }
                }
            }
        }

        if (parentCall != null && singleOrNull == callExpression) {
            runWriteActionIfPhysical(element) {
                commentSaver.restore(replaced, forceAdjustIndent = true/* by some reason lambda body is sometimes not properly indented */)
            }
            replaceSamConstructorCall(callExpression)
            val canMoveLambdaOutsideParentheses =
                allowAnalysisOnEdt {
                    allowAnalysisFromWriteAction {
                        analyze(parentCall) {
                            parentCall.canMoveLambdaOutsideParentheses()
                        }
                    }
                }
            if (canMoveLambdaOutsideParentheses) runWriteActionIfPhysical(element) {
                parentCall.moveFunctionLiteralOutsideParentheses()
            }
        } else {
            runWriteActionIfPhysical(element) {
                commentSaver.restore(replaced, forceAdjustIndent = true/* by some reason lambda body is sometimes not properly indented */)
            }
            pointerToReplaced.element?.let { replacedByPointer ->
                val endOffset = (replacedByPointer.callee.parent as? KtCallExpression)?.typeArgumentList?.endOffset
                    ?: replacedByPointer.callee.endOffset
                shortenReferencesInRange(replacedByPointer.containingKtFile, TextRange(replacedByPointer.startOffset, endOffset))
            }
        }
    }

    private val KtExpression.callee
        get() = getCalleeExpressionIfAny() as KtNameReferenceExpression
}

private data class Data(
    val baseTypeRef: KtTypeReference,
    val baseTypeClassId: ClassId,
    val typeRepresentation: String,
    val singleFunction: KtNamedFunction
)

@OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class, KaExperimentalApi::class)
private fun extractData(element: KtObjectLiteralExpression): Data? {
    val objectDeclaration = element.objectDeclaration

    val singleFunction = objectDeclaration.declarations.singleOrNull() as? KtNamedFunction ?: return null
    if (!singleFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return null

    val delegationSpecifier = objectDeclaration.superTypeListEntries.singleOrNull() ?: return null
    val typeRef = delegationSpecifier.typeReference ?: return null

    return allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(delegationSpecifier) {
                val type = typeRef.type
                val baseClassSymbol = type.expandedSymbol as? KaNamedClassSymbol
                val baseTypeClassId = baseClassSymbol?.classId ?: return null
                val samInterface = baseClassSymbol.findSamSymbolOrNull(false) ?: return null
                val origin = samInterface.origin
                // TODO: it has to be reconsidered testData/intentions/objectLiteralToLambda/NotJavaSAM.kt
                if (!origin.isJavaSourceOrLibrary() && !baseClassSymbol.isFun) return null
                // TODO: it should be WITH_QUALIFIED_NAMES but shortenReferencesInRange does not handle that properly
                val typeRepresentation = type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.OUT_VARIANCE)
                Data(typeRef, baseTypeClassId, typeRepresentation, singleFunction)
            }
        }
    }
}