// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.fixes.HLApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.fir.api.fixes.withInput
import org.jetbrains.kotlin.idea.fir.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.idea.quickfix.ChangeTypeFixUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ChangeTypeQuickFixFactories {
    val applicator = applicator<KtCallableDeclaration, Input> {
        familyName(CallableReturnTypeUpdaterApplicator.applicator.getFamilyName())

        actionName { declaration, (targetType, type) ->
            val presentation = getPresentation(targetType, declaration)
            getActionName(declaration, presentation, type)
        }

        applyTo { declaration, (_, type), project, editor ->
            CallableReturnTypeUpdaterApplicator.applicator.applyTo(declaration, type, project, editor)
        }
    }

    private fun getActionName(
        declaration: KtCallableDeclaration,
        presentation: String?,
        typeInfo: CallableReturnTypeUpdaterApplicator.TypeInfo
    ) = ChangeTypeFixUtils.getTextForQuickFix(
        declaration,
        presentation,
        typeInfo.defaultType.isUnit,
        typeInfo.defaultType.shortTypeRepresentation
    )

    private fun getPresentation(
        targetType: TargetType,
        declaration: KtCallableDeclaration
    ): String? {
        return when (targetType) {
            TargetType.CURRENT_DECLARATION -> null
            TargetType.BASE_DECLARATION -> KotlinBundle.message(
                "fix.change.return.type.presentation.base",
                declaration.presentationForQuickfix ?: return null
            )
            TargetType.ENCLOSING_DECLARATION -> KotlinBundle.message(
                "fix.change.return.type.presentation.enclosing",
                declaration.presentationForQuickfix ?: return KotlinBundle.message("fix.change.return.type.presentation.enclosing.function")
            )
            TargetType.CALLED_FUNCTION -> {
                val presentation =
                    declaration.presentationForQuickfix
                        ?: return KotlinBundle.message("fix.change.return.type.presentation.called.function")
                when (declaration) {
                    is KtParameter -> KotlinBundle.message("fix.change.return.type.presentation.accessed", presentation)
                    else -> KotlinBundle.message("fix.change.return.type.presentation.called", presentation)
                }
            }
            TargetType.VARIABLE -> return "'${declaration.name}'"
        }
    }

    private val KtCallableDeclaration.presentationForQuickfix: String?
        get() {
            val containerName = parentOfType<KtNamedDeclaration>()?.nameAsName?.takeUnless { it.isSpecial }
            return ChangeTypeFixUtils.functionOrConstructorParameterPresentation(this, containerName?.asString())
        }

    enum class TargetType {
        CURRENT_DECLARATION,
        BASE_DECLARATION,
        ENCLOSING_DECLARATION,
        CALLED_FUNCTION,
        VARIABLE,
    }

    data class Input(
        val targetType: TargetType,
        val typeInfo: CallableReturnTypeUpdaterApplicator.TypeInfo
    ) : HLApplicatorInput {
        override fun isValidFor(psi: PsiElement): Boolean = typeInfo.isValidFor(psi)
    }

    val changeFunctionReturnTypeOnOverride =
        changeReturnTypeOnOverride<KtFirDiagnostic.ReturnTypeMismatchOnOverride> {
            it.function as? KtFunctionSymbol
        }

    val changePropertyReturnTypeOnOverride =
        changeReturnTypeOnOverride<KtFirDiagnostic.PropertyTypeMismatchOnOverride> {
            it.property as? KtPropertySymbol
        }

    val changeVariableReturnTypeOnOverride =
        changeReturnTypeOnOverride<KtFirDiagnostic.VarTypeMismatchOnOverride> {
            it.variable as? KtPropertySymbol
        }

    val returnTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class, applicator) { diagnostic ->
            val function = diagnostic.targetFunction.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(function withInput Input(TargetType.ENCLOSING_DECLARATION, createTypeInfo(diagnostic.actualType)))
        }

    @OptIn(ExperimentalStdlibApi::class)
    val componentFunctionReturnTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.ComponentFunctionReturnTypeMismatch::class, applicator) { diagnostic ->
            val entryWithWrongType =
                getDestructuringDeclarationEntryThatTypeMismatchComponentFunction(
                    diagnostic.componentFunctionName,
                    diagnostic.psi
                )
                    ?: return@diagnosticFixFactory emptyList()
            buildList<HLApplicatorTargetWithInput<KtCallableDeclaration, Input>> {
                add(entryWithWrongType withInput Input(TargetType.VARIABLE, createTypeInfo(diagnostic.destructingType)))
                val classSymbol = (diagnostic.psi.getKtType() as? KtNonErrorClassType)?.classSymbol as? KtSymbolWithMembers ?: return@buildList
                val componentFunction = classSymbol.getMemberScope()
                    .getCallableSymbols { it == diagnostic.componentFunctionName }
                    .firstOrNull()?.psi as? KtCallableDeclaration
                    ?: return@buildList
                add(componentFunction withInput Input(TargetType.CALLED_FUNCTION, createTypeInfo(diagnostic.expectedType)))
            }
        }

    private inline fun <reified DIAGNOSTIC : KtDiagnosticWithPsi<KtNamedDeclaration>> changeReturnTypeOnOverride(
        crossinline getCallableSymbol: (DIAGNOSTIC) -> KtCallableSymbol?
    ) = diagnosticFixFactory(DIAGNOSTIC::class, applicator) { diagnostic ->
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
        val callable = getCallableSymbol(diagnostic) ?: return@diagnosticFixFactory emptyList()
        listOfNotNull(
            createChangeCurrentDeclarationQuickFix(callable, declaration),
            createChangeOverriddenFunctionQuickFix(callable),
        )
    }

    private fun <PSI : KtCallableDeclaration> KtAnalysisSession.createChangeCurrentDeclarationQuickFix(
        callable: KtCallableSymbol,
        declaration: PSI
    ): HLApplicatorTargetWithInput<PSI, Input>? {
        val lowerSuperType = findLowerBoundOfOverriddenCallablesReturnTypes(callable) ?: return null
        val changeToTypeInfo = createTypeInfo(lowerSuperType)
        return declaration withInput Input(TargetType.CURRENT_DECLARATION, changeToTypeInfo)
    }

    private fun KtAnalysisSession.createChangeOverriddenFunctionQuickFix(
        callable: KtCallableSymbol
    ): HLApplicatorTargetWithInput<KtCallableDeclaration, Input>? {
        val type = callable.returnType
        val singleNonMatchingOverriddenFunction = findSingleNonMatchingOverriddenFunction(callable, type) ?: return null
        val singleMatchingOverriddenFunctionPsi = singleNonMatchingOverriddenFunction.psiSafe<KtCallableDeclaration>() ?: return null
        val changeToTypeInfo = createTypeInfo(type)
        if (!singleMatchingOverriddenFunctionPsi.isWritable) return null
        return singleMatchingOverriddenFunctionPsi withInput Input(TargetType.BASE_DECLARATION, changeToTypeInfo)
    }

    private fun KtAnalysisSession.findSingleNonMatchingOverriddenFunction(
        callable: KtCallableSymbol,
        type: KtType
    ): KtCallableSymbol? {
        val overriddenSymbols = callable.getDirectlyOverriddenSymbols()
        return overriddenSymbols
            .singleOrNull { overridden ->
                !type.isSubTypeOf(overridden.returnType)
            }
    }

    private fun KtAnalysisSession.createTypeInfo(ktType: KtType) = with(CallableReturnTypeUpdaterApplicator.TypeInfo) {
        createByKtTypes(ktType)
    }

    private fun KtAnalysisSession.findLowerBoundOfOverriddenCallablesReturnTypes(symbol: KtCallableSymbol): KtType? {
        var lowestType: KtType? = null
        for (overridden in symbol.getDirectlyOverriddenSymbols()) {
            val overriddenType = overridden.returnType
            when {
                lowestType == null || overriddenType isSubTypeOf lowestType -> {
                    lowestType = overriddenType
                }
                lowestType isNotSubTypeOf overriddenType -> {
                    return null
                }
            }
        }
        return lowestType
    }

    private fun getDestructuringDeclarationEntryThatTypeMismatchComponentFunction(
        componentName: Name,
        rhsExpression: KtExpression
    ): KtDestructuringDeclarationEntry? {
        val componentIndex = componentName.asString().removePrefix("component").toIntOrNull() ?: return null
        val destructuringDeclaration = rhsExpression.getParentOfType<KtDestructuringDeclaration>(strict = true) ?: return null
        return destructuringDeclaration.entries[componentIndex - 1]
    }
}
