// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptionController
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.CommonClassNames.JAVA_LANG_OVERRIDE
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.impl.source.tree.SharedImplUtil
import com.intellij.psi.util.PropertyUtilBase
import com.intellij.psi.util.startOffset
import org.jdom.Element
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.KtSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.isPrivateOrPrivateToThis
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isRedundantBackticks
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.ConvertToBlockBodyUtils.ConvertExpressionToBlockBodyData
import org.jetbrains.kotlin.idea.codeinsight.utils.UnfoldFunctionCallToIfOrWhenUtils.singleExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.isNonNullableBooleanType
import org.jetbrains.kotlin.idea.core.NotPropertiesService
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.propertyNamesByAccessorName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.synthetic.canBePropertyAccessor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

private sealed class PropertyAccessorKind {
    class Setter(val valueArgumentExpression: KtExpression) : PropertyAccessorKind()
    sealed class Getter : PropertyAccessorKind() {
        data object IsGetter : PropertyAccessorKind()
        data object GetGetter : PropertyAccessorKind()
    }
}

class UsePropertyAccessSyntaxInspection : LocalInspectionTool(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : KtVisitorVoid() {

        override fun visitCallableReferenceExpression(callableReferenceExpression: KtCallableReferenceExpression) {
            checkCallableReferenceExpression(callableReferenceExpression, holder)
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            checkCallExpression(expression, holder)
        }
    }

    private fun checkCallableReferenceExpression(callableReferenceExpression: KtCallableReferenceExpression, holder: ProblemsHolder) {

        val callableReference = callableReferenceExpression.callableReference
        val mainReferenceOfCallableReference = callableReference.mainReference

        var methodName = mainReferenceOfCallableReference.element.text ?: return
        methodName = unquoteMethodNameIfNeeded(callableReference, methodName) ?: return
        if (!methodName.isSuitableAsPropertyAccessor()) return

        val propertyNames = findPropertyNames(methodName)
        if (propertyNames.isEmpty()) {
            return
        }

        val languageVersionSettings = callableReferenceExpression.languageVersionSettings
        if (!languageVersionSettings.supportsFeature(LanguageFeature.ReferencesToSyntheticJavaProperties)) return

        val referencedName = callableReference.getReferencedName()
        if (!PropertyUtilBase.isGetterName(referencedName)) {
            // Suggest converting only getters. Keep setters and is-getters untouched
            // Don't suggest replacing setters because setters and property references have different types
            // Don't suggest replacing is-getters because is-getter method reference and is-getter property references have the same syntax
            return
        }
        val propertyAccessorKind = PropertyAccessorKind.Getter.GetGetter

        analyze(callableReferenceExpression) {

            val expectedType = callableReferenceExpression.singleExpression()?.getExpectedType()
            if (expectedType?.isFunctionType != true && expectedType?.isFunctionalInterfaceType != true) return

            val symbol = mainReferenceOfCallableReference.resolveToSymbol() ?: return

            val symbolPsi = (symbol as? KtCallableSymbol)?.psi ?: return

            val problemHighlightType = getProblemHighlightType(symbolPsi)

            val receiverType =
                callableReferenceExpression.resolveCall()
                    ?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.dispatchReceiver?.type?.lowerBoundIfFlexible()
                    ?: callableReferenceExpression.getReceiverKtType()?.lowerBoundIfFlexible() ?: return

            val syntheticProperty = getSyntheticProperty(propertyNames, receiverType) ?: return

            if (!canConvert(symbol, callableReferenceExpression, receiverType, syntheticProperty.name.asString())) return

            holder.problem(callableReferenceExpression, getInspectionProblemText(propertyAccessorKind))
                .highlight(problemHighlightType)
                .range(
                    TextRange(
                        callableReference.startOffset,
                        callableReference.endOffset
                    ).shiftRight(-callableReferenceExpression.startOffset)
                )
                .fix(ReplaceWithPropertyAccessorFix(syntheticProperty.name, convertExpressionToBlockBodyData = null)).register()
        }
    }

    /**
     * Specific tests for KtReference type are:
     * dontReplaceGetterIfSameIdentifierExistsInScope1
     * dontReplaceGetterIfSameIdentifierExistsInScope2
     * dontReplaceSetterIfSameIdentifierExistsInScope
     * replaceGetterInBlockExpression
     */
    private fun checkCallExpression(callExpression: KtCallExpression, holder: ProblemsHolder) {

        val expressionParent = callExpression.parent

        val calleeExpression = callExpression.calleeExpression ?: return

        var methodName = calleeExpression.text ?: return
        methodName = unquoteMethodNameIfNeeded(calleeExpression, methodName) ?: return

        val propertyAccessorKind = getPropertyAccessorKind(callExpression, methodName) ?: return

        val qualifiedOrCall = callExpression.getQualifiedExpressionForSelectorOrThis()

        val propertyNames = findPropertyNames(methodName)
        if (propertyNames.isEmpty()) {
            return
        }

        if (propertyAccessorKind is PropertyAccessorKind.Setter) {
            if (expressionParent is KtDotQualifiedExpression) {
                if (expressionParent.parent is KtDotQualifiedExpression) {
                    // For J().setX(1).doSth()
                    // Dot qualified expression inside dot qualified expression, can't change
                    return
                } else {
                    if (expressionParent.getLeftMostReceiverExpression() == callExpression) {
                        // Like setA(1).setB(1)
                        // Setter is on the left side of dot qualified expression, can't change
                        return
                    }
                }
            }
            if (setterIsUsedAfterReturn(qualifiedOrCall)) return
        }

        analyze(callExpression) {
            val resolvedCall = callExpression.resolveCall() ?: return
            val resolvedFunctionCall = resolvedCall.successfulFunctionCallOrNull() ?: return

            val successfulFunctionCallSymbol = resolvedFunctionCall.symbol

            val resolvedCallPsi = successfulFunctionCallSymbol.psi ?: return

            val problemHighlightType = getProblemHighlightType(resolvedCallPsi)

            val returnType = successfulFunctionCallSymbol.returnType.lowerBoundIfFlexible()

            // For extension functions, receiver type taken such way is is null
            val receiverType = resolvedFunctionCall.partiallyAppliedSymbol.dispatchReceiver?.type?.lowerBoundIfFlexible() ?: return

            val syntheticProperty = getSyntheticProperty(propertyNames, receiverType) ?: return
            val syntheticPropertyName = syntheticProperty.name.asString()

            var convertExpressionToBlockBodyData: ConvertExpressionToBlockBodyData? = null

            if (propertyAccessorKind is PropertyAccessorKind.Setter) {
                val setterIsExpressionForFunction = methodIsExpressionForFunction(qualifiedOrCall)
                val setterIsExpressionForPropertyAccessor = methodIsExpressionForPropertyAccessor(qualifiedOrCall)
                if (setterIsExpressionForFunction || setterIsExpressionForPropertyAccessor) {
                    if (!returnType.isUnit) {
                        // Covered with the test "dontReplaceSetterForFunctionWithExpressionBody"
                        return
                    }
                    convertExpressionToBlockBodyData = ConvertExpressionToBlockBodyData(
                        returnType.isUnit,
                        returnType.isNothing,
                        returnType.isMarkedNullable,
                        returnType.render(position = Variance.OUT_VARIANCE)
                    )
                } else if (callExpression.isUsedAsExpression()) {
                    return
                }
            } else {
                if (propertyAccessorKind == PropertyAccessorKind.Getter.IsGetter) {
                    if (!returnType.isNonNullableBooleanType()) {
                        return
                    }
                }
                // Check that synthetic property won't be an expression for a new property with the same name like `val x = x`
                // Topical only for references
                if (expressionParent !is KtDotQualifiedExpression &&
                    callExpression.isUsedAsExpression() && (expressionParent as? KtProperty)?.name.equals(syntheticPropertyName)
                ) {
                    // Can't offer synthetic properties as right part of property in case names are same: like `val x = x`
                    return
                }
            }

            if (!canConvert(successfulFunctionCallSymbol, callExpression, receiverType, syntheticPropertyName)) return

            if (!syntheticPropertyTypeEqualsToExpected( // When KT-66587 is fixed, call this only for getters. See KTIJ-29110
                    syntheticProperty,
                    returnType,
                    propertyAccessorKind
                )
            ) return

            if (!propertyResolvesToSyntheticProperty(
                    callExpression,
                    propertyAccessorKind,
                    syntheticProperty.name,
                    receiverType
                )
            ) return

            holder.problem(callExpression, getInspectionProblemText(propertyAccessorKind))
                .highlight(problemHighlightType)
                .range(TextRange(calleeExpression.startOffset, calleeExpression.endOffset).shiftRight(-calleeExpression.startOffset))
                .fix(ReplaceWithPropertyAccessorFix(syntheticProperty.name, convertExpressionToBlockBodyData)).register()
        }
    }

    private class ReplaceWithPropertyAccessorFix(
        private val propertyName: Name,
        private val convertExpressionToBlockBodyData: ConvertExpressionToBlockBodyData?
    ) : PsiUpdateModCommandQuickFix() {
        override fun getName() = KotlinBundle.message("use.property.access.syntax")
        override fun getFamilyName() = name

        override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
            when (val ktExpression = element as? KtExpression) {
                is KtCallExpression -> {
                    when (ktExpression.valueArguments.size) {
                        0 -> replaceWithPropertyGet(ktExpression, propertyName)

                        1 -> {
                            val argumentExpression = ktExpression.valueArguments.single().getArgumentExpression()?.copy() as? KtExpression
                            if (argumentExpression == null) {
                                error("Error, setter argument can't be null")
                            }
                            replaceWithPropertySet(ktExpression, propertyName, argumentExpression)
                        }

                        else -> error("More than one argument in call to accessor")
                    }
                }

                is KtCallableReferenceExpression -> {
                    replaceWithPropertyGet(ktExpression.callableReference, propertyName)
                }

                else -> {
                    error("Can't parse $ktExpression (${ktExpression?.let { it::class }})")
                }
            }
        }

        private fun replaceWithPropertySet(
            callExpression: KtCallExpression,
            propertyName: Name,
            valueArgument: KtExpression
        ): KtExpression {
            val callToConvert = callExpression.convertExpressionBodyToBlockBodyIfPossible()

            val qualifiedExpression = callToConvert.getQualifiedExpressionForSelector()

            val psiFactory = KtPsiFactory(callToConvert.project)

            if (qualifiedExpression != null) {
                val pattern = when (qualifiedExpression) {
                    is KtDotQualifiedExpression -> "$0.$1=$2"
                    is KtSafeQualifiedExpression -> "$0?.$1=$2"
                    else -> error(qualifiedExpression)
                }

                val newExpression = psiFactory.createExpressionByPattern(
                    pattern,
                    qualifiedExpression.receiverExpression,
                    propertyName,
                    valueArgument,
                    reformat = true
                )
                return qualifiedExpression.replaced(newExpression)
            } else {
                val newExpression = psiFactory.createExpressionByPattern("$0=$1", propertyName, valueArgument)
                return callToConvert.replaced(newExpression)
            }
        }

        private fun KtCallExpression.convertExpressionBodyToBlockBodyIfPossible(): KtCallExpression {
            val call = getQualifiedExpressionForSelector() ?: this
            val callParent = call.parent
            if (callParent is KtDeclarationWithBody && call == callParent.bodyExpression) {
                if (convertExpressionToBlockBodyData == null) {
                    throw KotlinExceptionWithAttachments("Expected data to convert expression to a block body but it's null")
                }
                ConvertToBlockBodyUtils.convert(callParent, convertExpressionToBlockBodyData, true)
                val firstStatement = callParent.bodyBlockExpression?.statements?.first()
                return (firstStatement as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                    ?: firstStatement as? KtCallExpression
                    ?: throw KotlinExceptionWithAttachments("Unexpected contents of function after conversion: ${callParent::class.java}")
                        .withPsiAttachment("callParent", callParent)
            }
            return this
        }

        private fun replaceWithPropertyGet(oldElement: KtElement, propertyName: Name): KtExpression {
            val newExpression = KtPsiFactory(oldElement.project).createExpression(propertyName.render())
            return oldElement.replaced(newExpression)
        }
    }

    private fun unquoteMethodNameIfNeeded(expression: KtExpression, methodName: String): String? {
        return SharedImplUtil.getChildrenOfType(expression.node, KtTokens.IDENTIFIER).singleOrNull()?.let {
            if (isRedundantBackticks(it)) {
                return methodName.unquoteKotlinIdentifier()
            } else {
                return methodName
            }
        } ?: return null
    }

    /**
     * @param methodName should originate from the same @param call. This is done so for optimization purposes because taking methodName is
     * not trivial (it requires unquoting it from backticks if needed).
     * @see unquoteMethodNameIfNeeded
     */
    private fun getPropertyAccessorKind(call: KtCallExpression, methodName: String): PropertyAccessorKind? {
        if (!methodName.isSuitableAsPropertyAccessor()) return null

        val isSetUsage = PropertyUtilBase.isSetterName(methodName)

        val valueArguments = call.valueArguments
        if (isSetUsage) {
            if (valueArguments.size == 1) {
                val valueArgumentExpression = valueArguments.singleOrNull()?.getArgumentExpression()?.takeUnless {
                    it is KtLambdaExpression || it is KtNamedFunction || it is KtCallableReferenceExpression
                } ?: return null
                return PropertyAccessorKind.Setter(valueArgumentExpression)
            } else {
                return null
            }
        }

        if (valueArguments.isEmpty()) {
            if (PropertyUtilBase.isIsGetterName(methodName)) {
                return PropertyAccessorKind.Getter.IsGetter
            } else return PropertyAccessorKind.Getter.GetGetter
        } else {
            // More than 1 argument for getter
            return null
        }
    }

    /**
     * Several checks.
     */
    context(KtAnalysisSession)
    private fun canConvert(
        symbol: KtCallableSymbol,
        callExpression: KtExpression,
        receiverType: KtType,
        propertyName: String
    ): Boolean {
        val allOverriddenSymbols = listOf(symbol) + symbol.getAllOverriddenSymbols()
        if (functionOriginateNotFromJava(allOverriddenSymbols)) return false
        if (functionNameIsInNotPropertiesList(symbol, callExpression)) return false

        // Check that the receiver or its ancestors don't have public fields with the same name as the probable synthetic property
        if (receiverOrItsAncestorsContainVisibleFieldWithSameName(receiverType, propertyName)) return false
        return true
    }

    private fun getProblemHighlightType(psi: PsiElement): ProblemHighlightType {
        val problemHighlightType = if (reportNonTrivialAccessors || psi.isTrivialAccessor()) {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        } else {
            ProblemHighlightType.INFORMATION
        }
        return problemHighlightType
    }

    /**
     * Fixes the case from KTIJ-21051
     */
    context(KtAnalysisSession)
    private fun receiverOrItsAncestorsContainVisibleFieldWithSameName(receiverType: KtType, propertyName: String): Boolean {
        val fieldWithSameName = receiverType.getTypeScope()?.getDeclarationScope()?.getCallableSymbols()
            ?.filter { it is KtJavaFieldSymbol && it.name.toString() == propertyName && !it.visibility.isPrivateOrPrivateToThis() }
            ?.singleOrNull()
        return fieldWithSameName != null
    }

    context(KtAnalysisSession)
    private fun getSyntheticProperty(
        propertyNames: List<String>,
        receiverType: KtType
    ): KtSyntheticJavaPropertySymbol? {

        val syntheticJavaPropertiesScope = receiverType.getSyntheticJavaPropertiesScope() ?: return null

        val syntheticProperties = syntheticJavaPropertiesScope.getCallableSignatures(SCOPE_NAME_FILTER)

        val javaGetterAndSetterSymbols = syntheticProperties.mapTo(mutableSetOf()) { it.symbol }

        if (javaGetterAndSetterSymbols.isEmpty()) {
            return null
        }

        for (syntheticProperty in javaGetterAndSetterSymbols) {
            if (syntheticProperty is KtSyntheticJavaPropertySymbol) {
                val syntheticPropertyName = syntheticProperty.name.asString()
                if (propertyNames.contains(syntheticPropertyName)) {
                    if (KtTokens.KEYWORDS.types.any { it.toString() == syntheticPropertyName }) {
                        continue
                    } else {
                        return syntheticProperty
                    }
                }
            }
        }
        return null
    }

    context(KtAnalysisSession)
    private fun syntheticPropertyTypeEqualsToExpected(
        syntheticProperty: KtSyntheticJavaPropertySymbol,
        callReturnType: KtType,
        propertyAccessorKind: PropertyAccessorKind
    ): Boolean {
        val propertyExpectedType = if (propertyAccessorKind is PropertyAccessorKind.Setter) {
            propertyAccessorKind.valueArgumentExpression.getExpectedType() ?: return false
        } else {
            callReturnType
        }

        val syntheticPropertyReturnType = syntheticProperty.returnType.lowerBoundIfFlexible()
        return syntheticPropertyReturnType.isEqualTo(propertyExpectedType)
    }

    context(KtAnalysisSession)
    private fun propertyResolvesToSyntheticProperty(
        callExpression: KtExpression,
        propertyAccessorKind: PropertyAccessorKind,
        syntheticPropertyName: Name,
        receiverType: KtType
    ): Boolean {
        val qualifiedExpressionForSelector = callExpression.getQualifiedExpressionForSelector()
        val newExpression = if (propertyAccessorKind is PropertyAccessorKind.Setter) {
            if (qualifiedExpressionForSelector != null) {
                KtPsiFactory(callExpression.project).createExpressionByPattern(
                    if (qualifiedExpressionForSelector is KtSafeQualifiedExpression) {
                        "$0?.$1=$2"
                    } else {
                        "$0.$1=$2"
                    },
                    qualifiedExpressionForSelector.receiverExpression,
                    syntheticPropertyName,
                    propertyAccessorKind.valueArgumentExpression
                )
            } else {
                KtPsiFactory(callExpression.project).createExpressionByPattern(
                    "$0=$1",
                    syntheticPropertyName,
                    propertyAccessorKind.valueArgumentExpression
                )
            }
        } else { // For getters
            if (qualifiedExpressionForSelector != null) {
                return true // We don't need to check how this resolves (and nullable getters resolve to null)
            } else {
                KtPsiFactory(callExpression.project).createExpressionByPattern("$0", syntheticPropertyName)
            }
        }

        // This check is needed only for references because a synthetic property with reference might occasionally be hidden
        // by some variable or argument in the same scope
        if (qualifiedExpressionForSelector == null) {
            return receiverTypeOfNewExpressionEqualsToExpectedReceiverType(
                callExpression,
                newExpression,
                receiverType
            )
        } else {
            // Check that the call resolves without errors, for example, that we don't do `a?.stringProperty = 1`
            // After KTIJ-29110 is fixed, will be covered with tests propertyTypeIsMoreSpecific1 and propertyTypeIsMoreSpecific2
            return getSuccessfullyResolvedCall(qualifiedExpressionForSelector, newExpression) != null
        }
    }

    context(KtAnalysisSession)
    private fun getSuccessfullyResolvedCall(callExpression: KtExpression, newExpression: KtExpression): KtCallInfo? {
        val codeFragment =
            KtPsiFactory(callExpression.project).createExpressionCodeFragment(newExpression.text, callExpression)
        val contentElement = codeFragment.getContentElement() ?: return null
        val resolvedCall = contentElement.resolveCall() ?: return null
        if (resolvedCall is KtErrorCallInfo) {
            return null
        }
        return resolvedCall
    }

    context(KtAnalysisSession)
    private fun receiverTypeOfNewExpressionEqualsToExpectedReceiverType(
        callExpression: KtExpression,
        newExpression: KtExpression,
        expectedReceiverType: KtType
    ): Boolean {
        val resolvedCall = getSuccessfullyResolvedCall(callExpression, newExpression) ?: return false
        val replacementReceiverType =
            resolvedCall.successfulVariableAccessCall()?.partiallyAppliedSymbol?.dispatchReceiver?.type?.lowerBoundIfFlexible()
                ?: return false
        return replacementReceiverType.isEqualTo(expectedReceiverType)
    }

    private fun functionNameIsInNotPropertiesList(symbol: KtCallableSymbol, callExpression: KtExpression): Boolean {
        val symbolUnsafeName = symbol.callableIdIfNonLocal?.asSingleFqName()?.toUnsafe()

        val notProperties = NotPropertiesService.getNotProperties(callExpression)
        return symbolUnsafeName in notProperties
    }

    context(KtAnalysisSession)
    private fun functionOriginateNotFromJava(allOverriddenSymbols: List<KtCallableSymbol>): Boolean {
        for (overriddenSymbol in allOverriddenSymbols) {
            if (overriddenSymbol.origin == KtSymbolOrigin.JAVA) {
                val symbolAnnotations = overriddenSymbol.annotationsList.annotations
                if (symbolAnnotations.any { it.classId?.asFqNameString()?.equals(JAVA_LANG_OVERRIDE) == true }) {
                    // This is Java's @Override, continue searching for Java method but not overridden
                    continue
                } else {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Check if this set() is used after `return`
     * Covered with the test "dontReplaceSetterIfItGoesAfterReturn"
     */
    private fun setterIsUsedAfterReturn(expression: KtExpression): Boolean {
        val returnExpression = (expression.parent as? KtReturnExpression)?.returnedExpression
        val setIsInReturn = returnExpression?.equals(expression) == true
        return setIsInReturn
    }

    /**
     * Check if this method is used as an expression for a function like `fun setValue(x: Int) = test.setX(x)`
     */
    private fun methodIsExpressionForFunction(expression: KtExpression): Boolean {
        return (expression.parent as? KtFunction)?.bodyExpression == expression
    }

    /**
     * Check if this method is used as an expression for a property accessor like `set(value) = setName(value)`
     */
    private fun methodIsExpressionForPropertyAccessor(expression: KtExpression): Boolean {
        return (expression.parent as? KtPropertyAccessor)?.bodyExpression == expression
    }

    private fun findPropertyNames(methodName: String): List<String> {
        val name = Name.identifier(methodName)
        if (name.isSpecial) return emptyList()
        return propertyNamesByAccessorName(name).map { it.identifier }
    }

    private fun String.isSuitableAsPropertyAccessor(): Boolean =
        canBePropertyAccessor(this) && commonGetterLikePrefixes.none { prefix -> this.contains(prefix) }

    private val commonGetterLikePrefixes: Set<Regex> = setOf(
        "^getOr[A-Z]".toRegex(),
        "^getAnd[A-Z]".toRegex(),
        "^getIf[A-Z]".toRegex(),
    )

    private fun PsiElement.isTrivialAccessor(): Boolean {
        // Accessor is considered trivial if it has exactly one statement
        return when (this) {
            is KtNamedFunction -> bodyBlockExpression?.statements.orEmpty().size == 1

            is ClsMethodImpl -> {
                sourceMirrorMethod?.body?.statements?.let { it.size == 1 }
                    ?: true // skip compiled methods for which we can't get the source code
            }

            is PsiMethod -> body?.statements.orEmpty().size == 1

            else -> false
        }
    }

    @NlsSafe
    private fun getInspectionProblemText(propertyAccessorKind: PropertyAccessorKind): String =
        if (propertyAccessorKind is
                    PropertyAccessorKind.Setter
        ) KotlinBundle.message("use.of.setter.method.instead.of.property.access.syntax")
        else KotlinBundle.message("use.of.getter.method.instead.of.property.access.syntax")

    val propertiesNotToReplace = NotPropertiesService.DEFAULT.map(::FqNameUnsafe).toMutableSet()

    // Serialized setting
    @Suppress("MemberVisibilityCanBePrivate")
    var fqNameStrings = NotPropertiesService.DEFAULT.toMutableList()

    @Suppress("MemberVisibilityCanBePrivate")
    var reportNonTrivialAccessors = false

    override fun readSettings(node: Element) {
        super.readSettings(node)
        propertiesNotToReplace.clear()
        fqNameStrings.mapTo(propertiesNotToReplace, ::FqNameUnsafe)
    }

    override fun writeSettings(node: Element) {
        fqNameStrings.clear()
        propertiesNotToReplace.mapTo(fqNameStrings) { it.asString() }
        super.writeSettings(node)
    }

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.checkbox(
            "reportNonTrivialAccessors",
            KotlinBundle.message("use.property.access.syntax.option.report.non.trivial.accessors")
        ),
        OptPane.stringList("fqNameStrings", KotlinBundle.message("excluded.methods")),
    )

    override fun getOptionController(): OptionController {
        return super.getOptionController()
            .onValueSet("fqNameStrings") { newList ->
                assert(newList === fqNameStrings)
                propertiesNotToReplace.clear()
                fqNameStrings.mapTo(propertiesNotToReplace, ::FqNameUnsafe)
            }
    }

}

internal val SCOPE_NAME_FILTER: KtScopeNameFilter = { name -> !name.isSpecial }

class NotPropertiesServiceImpl(private val project: Project) : NotPropertiesService {
    override fun getNotProperties(element: PsiElement): Set<FqNameUnsafe> {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val tool = profile.getUnwrappedTool(USE_PROPERTY_ACCESS_INSPECTION, element)
        return (tool?.propertiesNotToReplace ?: NotPropertiesService.DEFAULT.map(::FqNameUnsafe)).toSet()
    }

    companion object {
        val USE_PROPERTY_ACCESS_INSPECTION: Key<UsePropertyAccessSyntaxInspection> = Key.create("UsePropertyAccessSyntax")
    }
}