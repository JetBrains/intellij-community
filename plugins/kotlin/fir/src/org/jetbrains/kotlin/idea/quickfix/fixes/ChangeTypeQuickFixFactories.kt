// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.quickfix.ChangeTypeFixUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

object ChangeTypeQuickFixFactories {
    enum class TargetType {
        CURRENT_DECLARATION,
        BASE_DECLARATION,
        ENCLOSING_DECLARATION,
        CALLED_FUNCTION,
        VARIABLE,
    }

    private class UpdateTypeQuickFix<E : KtCallableDeclaration>(
        target: E,
        private val targetType: TargetType,
        private val typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo,
    ) : KotlinApplicableQuickFix<E>(target) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.change.return.type.family")
        override fun getActionName(element: E): String = getActionName(element, targetType, typeInfo)
        override fun apply(element: E, project: Project, editor: Editor?, file: KtFile) =
            updateType(element, typeInfo, project, editor)
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
        diagnosticFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class) { diagnostic ->
            val declaration = diagnostic.targetFunction.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            listOf(UpdateTypeQuickFix(declaration, TargetType.ENCLOSING_DECLARATION, createTypeInfo(diagnostic.actualType)))
        }

    val componentFunctionReturnTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.ComponentFunctionReturnTypeMismatch::class) { diagnostic ->
            val entryWithWrongType =
                getDestructuringDeclarationEntryThatTypeMismatchComponentFunction(
                    diagnostic.componentFunctionName,
                    diagnostic.psi
                ) ?: return@diagnosticFixFactory emptyList()

            buildList {
                add(UpdateTypeQuickFix(entryWithWrongType, TargetType.VARIABLE, createTypeInfo(diagnostic.destructingType)))

                val classSymbol = (diagnostic.psi.getKtType() as? KtNonErrorClassType)?.classSymbol as? KtSymbolWithMembers ?: return@buildList
                val componentFunction = classSymbol.getMemberScope()
                    .getCallableSymbols { it == diagnostic.componentFunctionName }
                    .firstOrNull()?.psi as? KtCallableDeclaration
                    ?: return@buildList
                add(UpdateTypeQuickFix(componentFunction, TargetType.CALLED_FUNCTION, createTypeInfo(diagnostic.expectedType)))
            }
        }

    private inline fun <reified DIAGNOSTIC : KtDiagnosticWithPsi<KtNamedDeclaration>> changeReturnTypeOnOverride(
        crossinline getCallableSymbol: (DIAGNOSTIC) -> KtCallableSymbol?
    ) = diagnosticFixFactory(DIAGNOSTIC::class) { diagnostic ->
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
        val callable = getCallableSymbol(diagnostic) ?: return@diagnosticFixFactory emptyList()
        listOfNotNull(
            createChangeCurrentDeclarationQuickFix(callable, declaration),
            createChangeOverriddenFunctionQuickFix(callable),
        )
    }

    context(KtAnalysisSession)
    private fun <PSI : KtCallableDeclaration> createChangeCurrentDeclarationQuickFix(
        callable: KtCallableSymbol,
        declaration: PSI
    ): UpdateTypeQuickFix<PSI>? {
        val lowerSuperType = findLowerBoundOfOverriddenCallablesReturnTypes(callable) ?: return null
        return UpdateTypeQuickFix(declaration, TargetType.CURRENT_DECLARATION, createTypeInfo(lowerSuperType))
    }

    context(KtAnalysisSession)
    private fun createChangeOverriddenFunctionQuickFix(callable: KtCallableSymbol): UpdateTypeQuickFix<KtCallableDeclaration>? {
        val type = callable.returnType
        val singleNonMatchingOverriddenFunction = findSingleNonMatchingOverriddenFunction(callable, type) ?: return null
        val singleMatchingOverriddenFunctionPsi = singleNonMatchingOverriddenFunction.psiSafe<KtCallableDeclaration>() ?: return null
        if (!singleMatchingOverriddenFunctionPsi.isWritable) return null
        return UpdateTypeQuickFix(singleMatchingOverriddenFunctionPsi, TargetType.BASE_DECLARATION, createTypeInfo(type))
    }

    context(KtAnalysisSession)
    private fun findSingleNonMatchingOverriddenFunction(
        callable: KtCallableSymbol,
        type: KtType
    ): KtCallableSymbol? {
        val overriddenSymbols = callable.getDirectlyOverriddenSymbols()
        return overriddenSymbols
            .singleOrNull { overridden ->
                !type.isSubTypeOf(overridden.returnType)
            }
    }

    context(KtAnalysisSession)
    private fun createTypeInfo(ktType: KtType) = with(CallableReturnTypeUpdaterUtils.TypeInfo) {
        createByKtTypes(ktType)
    }

    context(KtAnalysisSession)
    private fun findLowerBoundOfOverriddenCallablesReturnTypes(symbol: KtCallableSymbol): KtType? {
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

    @IntentionName
    private fun getActionName(
        declaration: KtCallableDeclaration,
        targetType: TargetType,
        typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo
    ) = ChangeTypeFixUtils.getTextForQuickFix(
        declaration,
        getPresentation(targetType, declaration),
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
}
