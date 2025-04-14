// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility.PRIVATE
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility.PUBLIC
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.builtins.StandardNames.EQUALS_NAME
import org.jetbrains.kotlin.builtins.StandardNames.HASHCODE_NAME
import org.jetbrains.kotlin.builtins.StandardNames.TO_STRING_NAME
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.MODIFIER_KEYWORDS_ARRAY
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.load.java.propertyNamesByAccessorName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.synthetic.canBePropertyAccessor

internal class KotlinRedundantOverrideInspection : KotlinApplicableInspectionBase.Simple<KtNamedFunction, Unit>(), CleanupLocalInspectionTool {
    override fun getProblemDescription(element: KtNamedFunction, context: Unit): @InspectionMessage String =
        KotlinBundle.message("redundant.overriding.method")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        namedFunctionVisitor { function -> visitTargetElement(function, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean {
        val modifierList = element.modifierList ?: return false
        if (modifierList.getModifier(OVERRIDE_KEYWORD) == null) return false
        if (MODIFIER_EXCLUDE_OVERRIDE.any { modifierList.hasModifier(it) }) return false
        if (KotlinPsiHeuristics.hasNonSuppressAnnotations(element)) return false

        val qualifiedExpression = element.qualifiedExpression() ?: return false
        val superExpression = qualifiedExpression.receiverExpression as? KtSuperExpression ?: return false
        if (superExpression.superTypeQualifier != null) return false

        val superCallElement = qualifiedExpression.selectorExpression as? KtCallElement ?: return false
        if (!isSameFunctionName(superCallElement, element)) return false
        if (!isSameArguments(superCallElement, element)) return false

        return true
    }

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> {
        val funKeyword = element.funKeyword ?: return emptyList()
        val overrideKeyword = element.modifierList?.getModifier(OVERRIDE_KEYWORD) ?: return emptyList()
        return ApplicabilityRange.multiple(element) { listOf(funKeyword, overrideKeyword) }
    }

    override fun createQuickFix(element: KtNamedFunction, context: Unit): KotlinModCommandQuickFix<KtNamedFunction> =
        RedundantOverrideFix

    override fun KaSession.prepareContext(element: KtNamedFunction): Unit? {
        val symbol = element.symbol
        val qualifiedExpression = element.qualifiedExpression()
        val superCallElement = qualifiedExpression?.selectorExpression as? KtCallElement ?: return null
        val superCallInfo = superCallElement.resolveToCall() ?: return null
        val superFunctionCallOrNull = superCallInfo.singleFunctionCallOrNull() ?: return null
        val superFunctionSymbol = superFunctionCallOrNull.symbol
        val superFunctionIsAny = superFunctionSymbol.callableId in CALLABLE_IDS_OF_ANY

        if (element.containingClassOrObject?.isData() == true) {
            if (superFunctionIsAny) return null
            val allSuperOverriddenSymbols = superFunctionCallOrNull.symbol.allOverriddenSymbolsWithSelf
            if (allSuperOverriddenSymbols.any { it.callableId in CALLABLE_IDS_OF_ANY }) return null
        }

        if (hasDerivedProperty(element, symbol)) return null

        val superCallValueParameters = superFunctionCallOrNull.partiallyAppliedSymbol.signature.valueParameters
        val functionValueParameters = symbol.valueParameters

        if (functionValueParameters.size == superCallValueParameters.size &&
            functionValueParameters.zip(superCallValueParameters)
                .any {
                    val functionParameterType = it.first.returnType
                    val superParameterType = it.second.returnType
                    val typesMatch = functionParameterType.semanticallyEquals(superParameterType)
                    !typesMatch
                }
        ) return null

        val allFunctionOverriddenSymbols: Sequence<KaCallableSymbol> = symbol.allOverriddenSymbols
        // do nothing when the overridden function is from Any (e.g. `kotlin/Any.equals`)
        // and super function is abstract
        if (superFunctionIsAny && allFunctionOverriddenSymbols.any { it.modality == KaSymbolModality.ABSTRACT }) {
            return null
        }

        if (allFunctionOverriddenSymbols.any { it.isPackageVisibleNonJavaSymbol() }) {
            return null
        }

        if (isAmbiguouslyDerived(allFunctionOverriddenSymbols)) {
            return null
        }

        if (isOverridingDelegatedImplementation(element, allFunctionOverriddenSymbols)) {
            return null
        }

        return Unit
    }

    private fun KaCallableSymbol.isPackageVisibleNonJavaSymbol(): Boolean {
        if (!origin.isJavaSourceOrLibrary()) return false
        val symbolVisibility = visibility
        return symbolVisibility != PUBLIC && symbolVisibility != PRIVATE
    }

    private fun KtNamedFunction.qualifiedExpression(): KtDotQualifiedExpression? {
        val bodyExpression = bodyExpression ?: return null
        return when (bodyExpression) {
            is KtDotQualifiedExpression -> bodyExpression
            is KtBlockExpression -> {
                when (val body = bodyExpression.statements.singleOrNull()) {
                    is KtReturnExpression -> body.returnedExpression
                    is KtDotQualifiedExpression -> body.takeIf { _ ->
                        typeReference.let { it == null || it.text == "Unit" }
                    }

                    else -> null
                }
            }

            else -> null
        } as? KtDotQualifiedExpression
    }

    private fun isSameArguments(superCallElement: KtCallElement, function: KtNamedFunction): Boolean {
        val arguments = superCallElement.valueArguments
        val parameters = function.valueParameters
        if (arguments.size != parameters.size) return false
        return arguments.zip(parameters).all { (argument, parameter) ->
            argument.getArgumentExpression()?.text == parameter.name
        }
    }

    private fun isSameFunctionName(superSelectorExpression: KtCallElement, function: KtNamedFunction): Boolean {
        val superCallMethodName = superSelectorExpression.getCallNameExpression()?.text ?: return false
        return function.name == superCallMethodName
    }

    private fun KaSession.hasDerivedProperty(function: KtNamedFunction, functionSymbol: KaFunctionSymbol): Boolean {
        val functionName = function.nameAsName ?: return false
        if (!canBePropertyAccessor(functionName.asString())) return false
        val functionType = functionSymbol.returnType
        val isSetter = functionType.isUnitType
        val valueParameters = function.valueParameters
        val singleValueParameter = valueParameters.singleOrNull()
        if (isSetter && singleValueParameter == null || !isSetter && valueParameters.isNotEmpty()) return false
        val propertyType = if (isSetter) singleValueParameter!!.returnType else functionType
        val nonNullablePropertyType = propertyType.withNullability(KaTypeNullability.NON_NULLABLE)
        return propertyNamesByAccessorName(functionName).any {
            val propertyName = it.asString()
            function.containingClassOrObject?.declarations?.find { d ->
                d is KtProperty && d.name == propertyName && d.returnType.withNullability(KaTypeNullability.NON_NULLABLE)
                    .semanticallyEquals(nonNullablePropertyType)
            } != null
        }
    }

    private fun KaSession.isAmbiguouslyDerived(
        allFunctionOverriddenSymbols: Sequence<KaCallableSymbol>,
    ): Boolean {
        // less than 2 functions
        if (allFunctionOverriddenSymbols.take(2).count() < 2) return false

        // 2+ functions
        // At least one default in interface or abstract in class, or just something from Java
        return allFunctionOverriddenSymbols.any { overriddenSymbol ->
            val javaSourceOrLibrary = overriddenSymbol.origin.isJavaSourceOrLibrary()

            val kind = (overriddenSymbol.containingDeclaration as? KaNamedClassSymbol)?.classKind
            javaSourceOrLibrary || when (kind) {
                KaClassKind.CLASS -> overriddenSymbol.modality == KaSymbolModality.ABSTRACT
                KaClassKind.INTERFACE -> overriddenSymbol.modality != KaSymbolModality.ABSTRACT
                else -> false
            }
        }
    }

    /**
     * Don't mark an override unused if it overrides an implementation by delegation.
     *
     * Members from interfaces implemented by delegation and their super interfaces are affected.
     * Explicit overrides in this case replace the overrides from delegation and are not unused.
     */
    private fun KaSession.isOverridingDelegatedImplementation(
        function: KtNamedFunction,
        allFunctionOverriddenSymbols: Sequence<KaCallableSymbol>,
    ): Boolean {
        val superTypeListEntries = function.containingClassOrObject?.superTypeListEntries
        val delegatedSuperTypeEntries =
            superTypeListEntries.orEmpty().filterIsInstance<KtDelegatedSuperTypeEntry>().ifEmpty { return false }
        val delegatedSuperDeclarationTypes =
            delegatedSuperTypeEntries.mapNotNull { it.typeReference?.type }
        return allFunctionOverriddenSymbols.any { overriddenSymbol ->
            val containingSymbolType = (overriddenSymbol.containingSymbol as? KaNamedClassSymbol)?.defaultType ?: return@any false
            delegatedSuperDeclarationTypes.any { delegatedIFaceType -> delegatedIFaceType.isSubtypeOf(containingSymbolType) }
        }
    }
}

private object RedundantOverrideFix : KotlinModCommandQuickFix<KtNamedFunction>() {
    override fun getName(): String = KotlinBundle.message("redundant.override.fix.text")

    override fun getFamilyName(): String = name

    override fun applyFix(
        project: Project,
        element: KtNamedFunction,
        updater: ModPsiUpdater
    ) {
        element.delete()
    }
}

private val MODIFIER_EXCLUDE_OVERRIDE: List<KtModifierKeywordToken> = MODIFIER_KEYWORDS_ARRAY.asList() - OVERRIDE_KEYWORD
private val CALLABLE_IDS_OF_ANY: Set<CallableId> = listOf(EQUALS_NAME, HASHCODE_NAME, TO_STRING_NAME).mapTo(hashSetOf()) {
    CallableId(StandardClassIds.Any, it)
}
