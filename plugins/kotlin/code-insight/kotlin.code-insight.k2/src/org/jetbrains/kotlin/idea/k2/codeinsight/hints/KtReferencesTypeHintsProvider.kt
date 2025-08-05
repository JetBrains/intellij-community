// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.isAnyType
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
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
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

    @OptIn(ExperimentalContracts::class)
    private fun isFunctionParameter(e: PsiElement): Boolean {
        contract {
            returns(true) implies (e is KtParameter)
        }
        return e is KtParameter && e.typeReference == null && !e.isLoopParameter
    }

    fun collectFromFunctionParameter(
        element: PsiElement,
        sink: InlayTreeSink
    ) {
        if (!isFunctionParameter(element)) return

        sink.whenOptionEnabled(SHOW_FUNCTION_PARAMETER_TYPES.name) {
            element.nameIdentifier?.let {
                collectProvideTypeHint(element, it.endOffset, sink)
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

            sink.addPresentation(InlineInlayPosition(offset, true), hintFormat = HintFormat.default) {
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

context(_: KaSession)
private fun renderKtTypeHint(element: KtCallableDeclaration, multilineLocalProperty: Boolean): KaType? =
    calculateAllTypes<KaType>(element) { declarationType, allTypes, cannotBeNull ->
        if (declarationType is KaErrorType) return@calculateAllTypes null

        if (declarationType.isUnitType && multilineLocalProperty) {
            return@calculateAllTypes null
        }

        val name = (declarationType as? KaUsualClassType)?.classId?.relativeClassName?.shortName()
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

        if (ktType?.isAnyType == false && isUnclearType(ktType, element)) {
            ktType
        } else {
            null
        }
    }

context(_: KaSession)
private fun isUnclearType(type: KaType, element: KtCallableDeclaration): Boolean {
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
            val symbol: KaSymbol? = selectorExpression?.mainReference?.resolveToSymbol()
            if (symbol is KaEnumEntrySymbol) {
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
        val functionCall = functionLiteral.resolveToCall()?.singleFunctionCallOrNull() ?: return
        sink.addPresentation(InlineInlayPosition(lambdaExpression.endOffset, true), hintFormat = HintFormat.default) {
            text(": ")
            printKtType(functionCall.symbol.returnType)
        }
    }

}

context(_: KaSession)
private fun isConstructorCall(initializer: KtExpression?): Boolean {
    val callExpression = initializer as? KtCallExpression ?: return false
    val resolveCall = initializer.resolveToCall() ?: return false
    val functionCall = resolveCall.singleFunctionCallOrNull()
    if (functionCall?.symbol is KaSamConstructorSymbol) {
        return true
    }

    val constructorCall = resolveCall.singleConstructorCallOrNull()
    return constructorCall != null && (constructorCall.symbol.typeParameters.isEmpty() || callExpression.typeArgumentList != null)
}

context(_: KaSession)
private fun KtExpression.isClassOrPackageReference(): Boolean =
    when (this) {
        is KtNameReferenceExpression ->
            this.mainReference.resolveToSymbol()
                .let { it is KaClassLikeSymbol || it is KaPackageSymbol }
        is KtDotQualifiedExpression -> this.selectorExpression?.isClassOrPackageReference() ?: false
        else -> false
    }
