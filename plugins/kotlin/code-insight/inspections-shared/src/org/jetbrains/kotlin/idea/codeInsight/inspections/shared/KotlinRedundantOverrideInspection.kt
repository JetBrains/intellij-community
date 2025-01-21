// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isJavaSourceOrLibrary
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens.MODIFIER_KEYWORDS_ARRAY
import org.jetbrains.kotlin.lexer.KtTokens.OVERRIDE_KEYWORD
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
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
                if (function.containingClassOrObject?.isData() == true) {
                    if (superFunctionSymbol.callableId in CALLABLE_IDS_OF_ANY) return
                    val allSuperOverriddenSymbols = superFunctionCallOrNull.symbol.allOverriddenSymbols
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
                            //val bool = !functionParameterType.isSubtypeOf(superParameterType)
                            val bool = !functionParameterType.semanticallyEquals(superParameterType)
                            bool
                        }
                ) return

                val allFunctionOverriddenSymbols: Sequence<KaCallableSymbol> = symbol.allOverriddenSymbols
                if (allFunctionOverriddenSymbols.any { it.modality == KaSymbolModality.ABSTRACT }) {
                    if (superFunctionSymbol.callableId in CALLABLE_IDS_OF_ANY) {
                        return
                    }
                }
                if (allFunctionOverriddenSymbols.any {
                        if (!it.origin.isJavaSourceOrLibrary()) return@any false
                        val visibility = it.visibility
                        visibility != KaSymbolVisibility.PUBLIC && visibility != KaSymbolVisibility.PRIVATE
                    }) {
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
        return org.jetbrains.kotlin.load.java.propertyNamesByAccessorName(functionName).any {
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
        val iterator = allFunctionOverriddenSymbols.iterator()
        if (!iterator.hasNext()) return false
        iterator.next()
        if (!iterator.hasNext()) return false

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

        val delegatedSuperTypeEntries =
            containingClassOrObject?.superTypeListEntries?.filterIsInstance<KtDelegatedSuperTypeEntry>()?.takeIf { it.isNotEmpty() }
            ?: return false
        val delegatedSuperDeclarationTypes = delegatedSuperTypeEntries.mapNotNull { entry ->
            entry.typeReference?.type
        }
        return allFunctionOverriddenSymbols.any { overriddenSymbol ->
            val type = (overriddenSymbol.containingSymbol as? KaNamedClassSymbol)?.defaultType ?: return@any false
            delegatedSuperDeclarationTypes.any { type.isSubtypeOf(it) }
        }
    }

    private class RedundantOverrideFix : LocalQuickFix {
        override fun getName(): String = KotlinBundle.message("redundant.override.fix.text")
        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            descriptor.psiElement.delete()
        }
    }
}

private val MODIFIER_EXCLUDE_OVERRIDE: List<KtModifierKeywordToken> = MODIFIER_KEYWORDS_ARRAY.asList() - OVERRIDE_KEYWORD
private val CALLABLE_IDS_OF_ANY: Set<CallableId> = listOf("equals", "hashCode", "toString").mapTo(hashSetOf()) {
    CallableId(StandardClassIds.Any, Name.identifier(it))
}
