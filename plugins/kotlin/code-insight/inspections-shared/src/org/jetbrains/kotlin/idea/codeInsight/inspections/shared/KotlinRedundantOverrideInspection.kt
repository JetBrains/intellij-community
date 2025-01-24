// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
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
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.MODIFIER_KEYWORDS_ARRAY
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.load.java.propertyNamesByAccessorName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.synthetic.canBePropertyAccessor

internal class KotlinRedundantOverrideInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): KtVisitorVoid =
        namedFunctionVisitor(fun(function) {
            val funKeyword = function.funKeyword ?: return
            val modifierList = function.modifierList ?: return
            if (!modifierList.hasModifier(OVERRIDE_KEYWORD)) return
            if (MODIFIER_EXCLUDE_OVERRIDE.any { modifierList.hasModifier(it) }) return
            if (KotlinPsiHeuristics.hasNonSuppressAnnotations(function)) return

            val qualifiedExpression = function.qualifiedExpression() ?: return
            val superExpression = qualifiedExpression.receiverExpression as? KtSuperExpression ?: return
            if (superExpression.superTypeQualifier != null) return

            val superCallElement = qualifiedExpression.selectorExpression as? KtCallElement ?: return
            if (!isSameFunctionName(superCallElement, function)) return
            if (!isSameArguments(superCallElement, function)) return

            analyze(superCallElement) {
                val symbol = function.symbol
                val superCallInfo = superCallElement.resolveToCall() ?: return
                val superFunctionCallOrNull = superCallInfo.singleFunctionCallOrNull() ?: return
                val superFunctionSymbol = superFunctionCallOrNull.symbol
                val superFunctionIsAny = superFunctionSymbol.callableId in CALLABLE_IDS_OF_ANY

                if (function.containingClassOrObject?.isData() == true) {
                    if (superFunctionIsAny) return
                    val allSuperOverriddenSymbols = superFunctionCallOrNull.symbol.allOverriddenSymbolsWithSelf
                    if (allSuperOverriddenSymbols.any { it.callableId in CALLABLE_IDS_OF_ANY }) return
                }

                if (function.hasDerivedProperty(symbol)) return

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
                ) return

                val allFunctionOverriddenSymbols: Sequence<KaCallableSymbol> = symbol.allOverriddenSymbols
                // do nothing when the overridden function is from Any (e.g. `kotlin/Any.equals`)
                // and super function is abstract
                if (superFunctionIsAny && allFunctionOverriddenSymbols.any { it.modality == KaSymbolModality.ABSTRACT }) {
                    return
                }

                if (allFunctionOverriddenSymbols.any { it.isPackageVisibleNonJavaSymbol() }) {
                    return
                }

                if (function.isAmbiguouslyDerived(allFunctionOverriddenSymbols)) {
                    return
                }
            }
            val range = TextRange(modifierList.startOffsetInParent, funKeyword.endOffset - function.startOffset)
            val descriptor = holder.manager.createProblemDescriptor(
                function,
                range,
                KotlinBundle.message("redundant.overriding.method"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
                RedundantOverrideFix()
            )
            holder.registerProblem(descriptor)
        })

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

    context(KaSession)
    private fun KtNamedFunction.hasDerivedProperty(functionSymbol: KaFunctionSymbol): Boolean {
        val functionName = nameAsName ?: return false
        if (!canBePropertyAccessor(functionName.asString())) return false
        val functionType = functionSymbol.returnType
        val isSetter = functionType.isUnitType
        val valueParameters = valueParameters
        val singleValueParameter = valueParameters.singleOrNull()
        if (isSetter && singleValueParameter == null || !isSetter && valueParameters.isNotEmpty()) return false
        val propertyType = if (isSetter) singleValueParameter!!.returnType else functionType
        val nonNullablePropertyType = propertyType.withNullability(KaTypeNullability.NON_NULLABLE)
        return propertyNamesByAccessorName(functionName).any {
            val propertyName = it.asString()
            containingClassOrObject?.declarations?.find { d ->
                d is KtProperty && d.name == propertyName && d.returnType.withNullability(KaTypeNullability.NON_NULLABLE)
                    .semanticallyEquals(nonNullablePropertyType)
            } != null
        }
    }

    context(KaSession)
    private fun KtNamedFunction.isAmbiguouslyDerived(allFunctionOverriddenSymbols: Sequence<KaCallableSymbol>): Boolean {
        // less than 2 functions
        if (allFunctionOverriddenSymbols.take(2).count() < 2) return false

        // Two+ functions
        // At least one default in interface or abstract in class, or just something from Java
        if (allFunctionOverriddenSymbols.any { overriddenSymbol ->
                val javaSourceOrLibrary = overriddenSymbol.origin.isJavaSourceOrLibrary()

                val kind = (overriddenSymbol.containingDeclaration as? KaNamedClassSymbol)?.classKind
                javaSourceOrLibrary || when (kind) {
                    KaClassKind.CLASS -> overriddenSymbol.modality == KaSymbolModality.ABSTRACT
                    KaClassKind.INTERFACE -> overriddenSymbol.modality != KaSymbolModality.ABSTRACT
                    else -> false
                }

            }
        ) {
            return true
        }

        val superTypeListEntries = containingClassOrObject?.superTypeListEntries
        val delegatedSuperTypeEntries =
            superTypeListEntries?.filterIsInstance<KtDelegatedSuperTypeEntry>()?.ifEmpty { return false } ?: return false
        val delegatedSuperDeclarationTypes =
            delegatedSuperTypeEntries.mapNotNull { it.typeReference?.type }
        return allFunctionOverriddenSymbols.any { overriddenSymbol ->
            val type = (overriddenSymbol.containingSymbol as? KaNamedClassSymbol)?.defaultType ?: return@any false
            delegatedSuperDeclarationTypes.any { type.isSubtypeOf(it) }
        }
    }

    private class RedundantOverrideFix : KotlinModCommandQuickFix<KtNamedFunction>() {
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
}

private val MODIFIER_EXCLUDE_OVERRIDE: List<KtModifierKeywordToken> = MODIFIER_KEYWORDS_ARRAY.asList() - OVERRIDE_KEYWORD
private val CALLABLE_IDS_OF_ANY: Set<CallableId> = listOf(EQUALS_NAME, HASHCODE_NAME, TO_STRING_NAME).mapTo(hashSetOf()) {
    CallableId(StandardClassIds.Any, it)
}
