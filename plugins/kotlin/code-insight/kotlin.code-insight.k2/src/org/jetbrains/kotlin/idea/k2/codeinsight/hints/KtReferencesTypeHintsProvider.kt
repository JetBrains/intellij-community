// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_FUNCTION_PARAMETER_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_FUNCTION_RETURN_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_LOCAL_VARIABLE_TYPES
import org.jetbrains.kotlin.idea.codeInsight.hints.SHOW_PROPERTY_TYPES
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.calculateAllTypes
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KtReferencesTypeHintsProvider: AbstractKtInlayHintsProvider() {
    override fun collectFromElement(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        collectFromPropertyType(element, sink)
        collectFromLocalVariable(element, sink)
        collectFromFunction(element, sink)
        collectFromFunctionParameter(element, sink)
    }

    private fun isPropertyType(e: PsiElement): Boolean =
        e is KtProperty && e.getReturnTypeReference() == null && !e.isLocal

    private fun collectFromPropertyType(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isPropertyType(element)) return

        val property: KtCallableDeclaration = element as? KtCallableDeclaration ?: return
        sink.whenOptionEnabled(SHOW_PROPERTY_TYPES.name) {
            property.nameIdentifier?.let {
                collectProvideTypeHint(property, it.endOffset, sink)
            }
        }
    }

    private fun isLocalVariable(e: PsiElement): Boolean =
        (e is KtProperty && e.getReturnTypeReference() == null && e.isLocal) ||
                (e is KtParameter && e.isLoopParameter && e.typeReference == null) ||
                (e is KtDestructuringDeclarationEntry && e.getReturnTypeReference() == null && e.name != "_")

    private fun collectFromLocalVariable(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isLocalVariable(element)) return

        val property: KtCallableDeclaration = element as? KtCallableDeclaration ?: return
        property.nameIdentifier?.let {
            sink.whenOptionEnabled(SHOW_LOCAL_VARIABLE_TYPES.name) {
                collectProvideTypeHint(property, it.endOffset, sink)
            }
        }
    }

    private fun isFunction(e: PsiElement): Boolean {
        return e is KtNamedFunction && !(e.hasBlockBody() || e.hasDeclaredReturnType()) ||
                Registry.`is`("kotlin.enable.inlay.hint.for.lambda.return.type") &&
                e is KtExpression &&
                e !is KtFunctionLiteral &&
                !e.isNameReferenceInCall() &&
                e.isLambdaReturnValueHintsApplicable(allowOneLiner = true)
    }

    private fun collectFromFunction(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isFunction(element)) return

        val namedFunction = element as? KtNamedFunction
        val functionValueParameterList = namedFunction?.valueParameterList
        sink.whenOptionEnabled(SHOW_FUNCTION_RETURN_TYPES.name) {
            functionValueParameterList?.let {
                collectProvideTypeHint(namedFunction, it.endOffset, sink)
            }

            val lambdaExpression = (element as? KtExpression)?.takeIf {
                it.isLambdaReturnValueHintsApplicable(allowOneLiner = true)
            }

            lambdaExpression?.let {
                collectLambdaTypeHint(it, sink)
            }
        }
    }

    private fun isFunctionParameter(e: PsiElement): Boolean =
        e is KtParameter && e.typeReference == null && !e.isLoopParameter

    fun collectFromFunctionParameter(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isFunctionParameter(element)) return

        val parameter = element as? KtParameter ?: return

        sink.whenOptionEnabled(SHOW_FUNCTION_PARAMETER_TYPES.name) {
            parameter.nameIdentifier?.let {
                collectProvideTypeHint(parameter, it.endOffset, sink)
            }
        }
    }
}

@ApiStatus.Internal
internal fun KtNamedDeclaration.getReturnTypeReference() = getReturnTypeReferences().singleOrNull()

@ApiStatus.Internal
internal fun KtNamedDeclaration.getReturnTypeReferences(): List<KtTypeReference> =
    when (this) {
        is KtCallableDeclaration -> listOfNotNull(typeReference)
        is KtClassOrObject -> superTypeListEntries.mapNotNull { it.typeReference }
        is KtScript -> emptyList()
        else -> throw AssertionError("Unexpected declaration kind: $text")
    }

@ApiStatus.Internal
internal fun PsiElement.isNameReferenceInCall() =
    this is KtNameReferenceExpression && parent is KtCallExpression

@ApiStatus.Internal
internal fun KtExpression.isLambdaReturnValueHintsApplicable(allowOneLiner: Boolean = false): Boolean {
    //if (allowOneLiner && this.isOneLiner()) {
    //    val literalWithBody = this is KtBlockExpression && isFunctionalLiteralWithBody()
    //    return literalWithBody
    //}

    if (this is KtWhenExpression) {
        return false
    }

    if (this is KtBlockExpression) {
        if (allowOneLiner && this.isOneLiner()) {
            return isFunctionalLiteralWithBody()
        }
        return false
    }

    if (this is KtIfExpression && !this.isOneLiner()) {
        return false
    }

    if (this.getParentOfType<KtIfExpression>(true)?.isOneLiner() == true) {
        return false
    }

    if (!KtPsiUtil.isStatement(this)) {
        if (!allowLabelOnExpressionPart(this)) {
            return false
        }
    } else if (forceLabelOnExpressionPart(this)) {
        return false
    }
    return isFunctionalLiteralWithBody()
}

private fun KtExpression.isFunctionalLiteralWithBody(): Boolean {
    val functionLiteral = this.getParentOfType<KtFunctionLiteral>(true)
    val body = functionLiteral?.bodyExpression ?: return false
    return !(body.statements.size == 1 && body.statements[0] == this)
}

private fun allowLabelOnExpressionPart(expression: KtExpression): Boolean {
    val parent = expression.parent as? KtExpression ?: return false
    return expression == expressionStatementPart(parent)
}

private fun forceLabelOnExpressionPart(expression: KtExpression): Boolean {
    return expressionStatementPart(expression) != null
}

private fun expressionStatementPart(expression: KtExpression): KtExpression? {
    val splitPart: KtExpression = when (expression) {
        is KtAnnotatedExpression -> expression.baseExpression
        is KtLabeledExpression -> expression.baseExpression
        else -> null
    } ?: return null

    if (!isNewLineBeforeExpression(splitPart)) {
        return null
    }

    return splitPart
}

private fun isNewLineBeforeExpression(expression: KtExpression): Boolean {
    val whiteSpace = expression.node.treePrev?.psi as? PsiWhiteSpace ?: return false
    return whiteSpace.text.contains("\n")
}

internal fun collectProvideTypeHint(element: KtCallableDeclaration, offset: Int, sink: InlayTreeSink) {
    val multilineLocalProperty = isMultilineLocalProperty(element)

    analyze(element) {
        renderKtTypeHint(element, multilineLocalProperty)?.let { ktType ->
            val settings = element.containingKtFile.kotlinCustomSettings

            val prefix = buildString {
                if (settings.SPACE_BEFORE_TYPE_COLON) {
                    append(" ")
                }

                append(":")
                if (settings.SPACE_AFTER_TYPE_COLON) {
                    append(" ")
                }
            }

            sink.addPresentation(InlineInlayPosition(offset, true), hasBackground = true) {
                text(prefix)
                printKtType(ktType)
            }
        }
    }
}

private fun isMultilineLocalProperty(element: PsiElement): Boolean {
    if (element is KtProperty && element.isLocal && element.isMultiLine()) {
        val propertyLine = element.getLineNumber()
        val equalsTokenLine = element.equalsToken?.getLineNumber() ?: -1
        val initializerLine = element.initializer?.getLineNumber() ?: -1
        if (propertyLine == equalsTokenLine && propertyLine != initializerLine) {
            val indentBeforeProperty = (element.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n')
            val indentBeforeInitializer = (element.initializer?.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n')
            if (indentBeforeProperty == indentBeforeInitializer) {
                return true
            }
        }
    }
    return false
}

context(KtAnalysisSession)
private fun renderKtTypeHint(element: KtCallableDeclaration, multilineLocalProperty: Boolean): KtType? =
    calculateAllTypes<KtType>(element) { declarationType, allTypes, cannotBeNull ->
        if (declarationType is KtErrorType) return@calculateAllTypes null

        if (declarationType.isUnit && multilineLocalProperty) {
            return@calculateAllTypes null
        }

        val name = (declarationType as? KtUsualClassType)?.classId?.relativeClassName?.shortName()
        val ktType = when {
            name == SpecialNames.NO_NAME_PROVIDED -> {
                if (element is KtProperty && element.isLocal) {
                    // for local variables, an anonymous object type is not collapsed to its supertype,
                    // so showing the supertype will be misleading
                    return@calculateAllTypes null
                }
                allTypes.singleOrNull()
            }

            name?.isSpecial == true -> {
                allTypes.firstOrNull()
            }

            else -> declarationType
        }

        if (ktType?.isAny == false && isUnclearType(ktType, element)) {
            ktType
        } else {
            null
        }
    }

context(KtAnalysisSession)
private fun isUnclearType(type: KtType, element: KtCallableDeclaration): Boolean {
    if (element !is KtProperty) return true

    val initializer = element.initializer ?: return true
    if (initializer is KtConstantExpression || initializer is KtStringTemplateExpression) return false
    if (initializer is KtUnaryExpression && initializer.baseExpression is KtConstantExpression) return false

    if (isConstructorCall(initializer)) {
        return false
    }

    if (initializer is KtDotQualifiedExpression) {
        val selectorExpression = initializer.selectorExpression
        if (type.isEnum()) {
            // Do not show type for enums if initializer has enum entry with explicit enum name: val p = Enum.ENTRY
            val symbol: KtSymbol? = selectorExpression?.mainReference?.resolveToSymbol()
            if (symbol is KtEnumEntrySymbol) {
                return false
            }
        }

        if (initializer.receiverExpression.isClassOrPackageReference() && isConstructorCall(selectorExpression)) {
            return false
        }
    }

    return true
}

internal fun collectLambdaTypeHint(lambdaExpression: KtExpression, sink: InlayTreeSink) {
    val functionLiteral = lambdaExpression.getStrictParentOfType<KtFunctionLiteral>() ?: return

    analyze(lambdaExpression) {
        val functionCall = functionLiteral.resolveCallOld()?.singleFunctionCallOrNull() ?: return
        sink.addPresentation(InlineInlayPosition(lambdaExpression.endOffset, true), hasBackground = true) {
            text(": ")
            printKtType(functionCall.symbol.returnType)
        }
    }

}

context(KtAnalysisSession)
private fun isConstructorCall(initializer: KtExpression?): Boolean {
    val callExpression = initializer as? KtCallExpression ?: return false
    val resolveCall = initializer.resolveCallOld() ?: return false
    val functionCall = resolveCall.singleFunctionCallOrNull()
    if (functionCall?.symbol is KtSamConstructorSymbol) {
        return true
    }

    val constructorCall = resolveCall.singleConstructorCallOrNull()
    return constructorCall != null && (constructorCall.symbol.typeParameters.isEmpty() || callExpression.typeArgumentList != null)
}

context(KtAnalysisSession)
private fun KtExpression.isClassOrPackageReference(): Boolean =
    when (this) {
        is KtNameReferenceExpression ->
            this.mainReference.resolveToSymbol()
                .let { it is KtClassLikeSymbol || it is KtPackageSymbol }
        is KtDotQualifiedExpression -> this.selectorExpression?.isClassOrPackageReference() ?: false
        else -> false
    }
