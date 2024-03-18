// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.quickfix.ChangeTypeFixUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isNull

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
    ) : AbstractKotlinApplicableQuickFix<E>(target) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.change.return.type.family")
        override fun getActionName(element: E): String = getActionName(element, targetType, typeInfo)
        override fun apply(element: E, project: Project, editor: Editor?, file: KtFile) =
            updateType(element, typeInfo, project, editor)
    }

    val changeFunctionReturnTypeOnOverride = changeReturnTypeOnOverride<KtFirDiagnostic.ReturnTypeMismatchOnOverride>(
        getCallableSymbol = { it.function as KtFunctionSymbol },
        getSuperCallableSymbol = { it.superFunction as KtFunctionSymbol },
    )

    val changePropertyReturnTypeOnOverride = changeReturnTypeOnOverride<KtFirDiagnostic.PropertyTypeMismatchOnOverride>(
        getCallableSymbol = { it.property as KtPropertySymbol },
        getSuperCallableSymbol = { it.superProperty as KtPropertySymbol },
    )

    val changeVariableReturnTypeOnOverride = changeReturnTypeOnOverride<KtFirDiagnostic.VarTypeMismatchOnOverride>(
        getCallableSymbol = { it.variable as KtPropertySymbol },
        getSuperCallableSymbol = { it.superVariable as KtPropertySymbol },
    )

    context(KtAnalysisSession)
    private fun getActualType(ktType: KtType): KtType {
        val typeKind = ktType.functionTypeKind
        when (typeKind) {
            FunctionTypeKind.KFunction -> typeKind.nonReflectKind()
            FunctionTypeKind.KSuspendFunction -> typeKind.nonReflectKind()
            else -> null
        }?.let {
            val functionalType = ktType as KtFunctionalType
            return buildClassType(it.numberedClassId((functionalType).arity)) {
                functionalType.parameterTypes.forEach { arg ->
                    argument(arg)
                }
                argument(functionalType.returnType)
            }
        }
        return ktType.approximateToSuperPublicDenotableOrSelf(true)
    }

    context(KtAnalysisSession)
    private fun KtElement.returnType(candidateType: KtType): KtType {
        val (initializers, functionOrGetter) = when (this) {
            is KtNamedFunction -> listOfNotNull(this.initializer) to this
            is KtProperty -> listOfNotNull(this.initializer, this.getter?.initializer) to this.getter
            is KtPropertyAccessor -> listOfNotNull(this.initializer) to this
            else -> return candidateType
        }
        val returnedExpressions = if (functionOrGetter != null) {
            val declarationSymbol = functionOrGetter.getSymbol()
            functionOrGetter
                .collectDescendantsOfType<KtReturnExpression> { it.getReturnTargetSymbol() == declarationSymbol }
                .mapNotNull { it.returnedExpression }
                .plus(initializers)
        } else {
            initializers
        }.map { KtPsiUtil.safeDeparenthesize(it) }

        returnedExpressions.singleOrNull()?.let {
            if (it.isNull() || this is KtCallableDeclaration && this.typeReference == null || this is KtPropertyAccessor && this.returnTypeReference == null) return candidateType
        }

        val property = this as? KtProperty
        val returnTypes = buildList {
            addAll(returnedExpressions.mapNotNull { returnExpr ->
                (property?.let { it.getPropertyInitializerType() } ?: returnExpr.getKtType())?.let { getActualType(it) }
            })
            if (!candidateType.isUnit) {
                add(candidateType)
            }
        }.distinct()
        return commonSuperType(returnTypes) ?: candidateType
    }

    context(KtAnalysisSession)
    private fun KtProperty.getPropertyInitializerType(): KtType? {
        val initializer = initializer
        return if (typeReference != null && initializer != null) {
            //copy property initializer to calculate initializer's type without property's declared type
            KtPsiFactory(project).createExpressionCodeFragment(initializer.text, this).getContentElement()?.getKtType()
        } else null
    }

    val returnTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.ReturnTypeMismatch::class) { diagnostic ->
            val element = diagnostic.targetFunction.psi as? KtElement ?: return@diagnosticFixFactory emptyList()
            val declaration = element as? KtCallableDeclaration ?: (element as? KtPropertyAccessor)?.property  ?: return@diagnosticFixFactory emptyList()
            listOf(UpdateTypeQuickFix(declaration, if (element is KtPropertyAccessor) TargetType.VARIABLE else TargetType.ENCLOSING_DECLARATION, createTypeInfo(element.returnType(getActualType(diagnostic.actualType)))))
        }

    val returnTypeNullableTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.NullForNonnullType::class) { diagnostic ->
            val returnExpr = diagnostic.psi.parentOfType<KtReturnExpression>() ?: return@diagnosticFixFactory emptyList()
            val declaration = returnExpr.getReturnTargetSymbol()?.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
            val withNullability = diagnostic.expectedType.withNullability(KtTypeNullability.NULLABLE)
            listOf(UpdateTypeQuickFix(declaration, TargetType.ENCLOSING_DECLARATION, createTypeInfo(declaration.returnType(withNullability))))
        }

    val initializerTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.InitializerTypeMismatch::class) { diagnostic ->
            val declaration = diagnostic.psi as? KtProperty ?: return@diagnosticFixFactory emptyList()
            registerVariableTypeFixes(declaration, getActualType(diagnostic.actualType))
        }

    val assignmentTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.AssignmentTypeMismatch::class) { diagnostic ->
            val expression = diagnostic.psi
            val assignment = expression.parent as? KtBinaryExpression ?: return@diagnosticFixFactory emptyList()
            val declaration = (assignment.left as? KtNameReferenceExpression)?.mainReference?.resolve() as? KtProperty ?: return@diagnosticFixFactory emptyList()
            if (!declaration.isVar || declaration.typeReference != null) return@diagnosticFixFactory emptyList()
            val actualType = getActualType(diagnostic.actualType)
            val type = if (declaration.initializer?.isNull() == true) actualType.withNullability(KtTypeNullability.NULLABLE) else actualType
            registerVariableTypeFixes(declaration, type)
        }

    val typeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.TypeMismatch::class) { diagnostic ->
            val expr = diagnostic.psi
            val property = expr.parent as? KtProperty ?: return@diagnosticFixFactory emptyList()
            val actualType = property.getPropertyInitializerType() ?: diagnostic.actualType
            registerVariableTypeFixes(property, getActualType(actualType))
        }

    context(KtAnalysisSession)
    private fun registerVariableTypeFixes(declaration: KtProperty, type: KtType): List<KotlinQuickFixAction<KtExpression>> {
        val expectedType = declaration.getReturnKtType()
        val expression = declaration.initializer
        return buildList {
            add(UpdateTypeQuickFix(declaration, TargetType.VARIABLE, createTypeInfo(declaration.returnType(type))))
            if (expression is KtConstantExpression && expectedType.isNumberOrUNumberType() && type.isNumberOrUNumberType()) {
                add(WrongPrimitiveLiteralFix(expression, preparePrimitiveLiteral(expression, expectedType)))
            }
        }
    }

    val parameterTypeMismatch =
        diagnosticFixFactory(KtFirDiagnostic.ArgumentTypeMismatch::class) { diagnostic ->
            val expression = diagnostic.psi
            val actualType = getActualType(diagnostic.actualType)
            val expectedType = diagnostic.expectedType
            buildList {
                if (expression is KtConstantExpression && expectedType.isNumberOrUNumberType() && actualType.isNumberOrUNumberType()) {
                    add(WrongPrimitiveLiteralFix(expression, preparePrimitiveLiteral(expression, expectedType)))
                }
            }
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
                    .getCallableSymbols(diagnostic.componentFunctionName)
                    .firstOrNull()?.psi as? KtCallableDeclaration
                    ?: return@buildList
                add(UpdateTypeQuickFix(componentFunction, TargetType.CALLED_FUNCTION, createTypeInfo(diagnostic.expectedType)))
            }
        }

    private inline fun <reified DIAGNOSTIC : KtDiagnosticWithPsi<KtNamedDeclaration>> changeReturnTypeOnOverride(
        crossinline getCallableSymbol: (DIAGNOSTIC) -> KtCallableSymbol,
        crossinline getSuperCallableSymbol: (DIAGNOSTIC) -> KtCallableSymbol,
    ) = diagnosticFixFactory(DIAGNOSTIC::class) { diagnostic ->
        val declaration = diagnostic.psi as? KtCallableDeclaration ?: return@diagnosticFixFactory emptyList()
        val callable = getCallableSymbol(diagnostic)
        val superCallable = getSuperCallableSymbol(diagnostic)
        listOfNotNull(
            createChangeCurrentDeclarationQuickFix(superCallable, declaration),
            createChangeOverriddenFunctionQuickFix(callable, superCallable),
        )
    }

    context(KtAnalysisSession)
    private fun <PSI : KtCallableDeclaration> createChangeCurrentDeclarationQuickFix(
        superCallable: KtCallableSymbol,
        declaration: PSI
    ): UpdateTypeQuickFix<PSI>? {
        return UpdateTypeQuickFix(declaration, TargetType.CURRENT_DECLARATION, createTypeInfo(superCallable.returnType))
    }

    context(KtAnalysisSession)
    private fun createChangeOverriddenFunctionQuickFix(
        callable: KtCallableSymbol,
        superCallable: KtCallableSymbol,
    ): UpdateTypeQuickFix<KtCallableDeclaration>? {
        val type = callable.returnType
        val singleMatchingOverriddenFunctionPsi = superCallable.psiSafe<KtCallableDeclaration>() ?: return null
        if (!singleMatchingOverriddenFunctionPsi.isWritable) return null
        return UpdateTypeQuickFix(singleMatchingOverriddenFunctionPsi, TargetType.BASE_DECLARATION, createTypeInfo(type))
    }

    context(KtAnalysisSession)
    private fun createTypeInfo(ktType: KtType) = with(CallableReturnTypeUpdaterUtils.TypeInfo) {
        createByKtTypes(ktType)
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
            TargetType.VARIABLE -> {
                val containerName = declaration.parentOfType<KtClassOrObject>()?.nameAsName?.takeUnless { it.isSpecial }?.asString()
                return "'${containerName?.let { "$containerName." } ?: ""}${declaration.name}'"
            }
        }
    }

    private val KtCallableDeclaration.presentationForQuickfix: String?
        get() {
            val containerName = parentOfType<KtClassOrObject>()?.nameAsName?.takeUnless { it.isSpecial }
            return ChangeTypeFixUtils.functionOrConstructorParameterPresentation(this, containerName?.asString())
        }
}

context(KtAnalysisSession)
fun KtType.isNumberOrUNumberType(): Boolean = isNumberType() || isUNumberType()

context(KtAnalysisSession)
fun KtType.isNumberType(): Boolean = isPrimitive && !isBoolean && !isChar

context(KtAnalysisSession)
fun KtType.isUNumberType(): Boolean = isUByte || isUShort || isUInt || isULong
