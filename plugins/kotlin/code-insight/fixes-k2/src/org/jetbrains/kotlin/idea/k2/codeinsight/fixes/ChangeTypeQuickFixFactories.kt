// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.quickfix.ChangeTypeFixUtils
import org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.types.Variance

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
    ) : PsiUpdateModCommandAction<E>(target) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.change.return.type.family")
        override fun getPresentation(context: ActionContext, element: E): Presentation =
            Presentation.of(getActionName(element, targetType, typeInfo))
        override fun invoke(context: ActionContext, element: E, updater: ModPsiUpdater) =
            updateType(element, typeInfo, context.project)
    }

    val changeFunctionReturnTypeOnOverride = changeReturnTypeOnOverride<KaFirDiagnostic.ReturnTypeMismatchOnOverride>(
        getCallableSymbol = { it.function as KaNamedFunctionSymbol },
        getSuperCallableSymbol = { it.superFunction as KaNamedFunctionSymbol },
    )

    val changePropertyReturnTypeOnOverride = changeReturnTypeOnOverride<KaFirDiagnostic.PropertyTypeMismatchOnOverride>(
        getCallableSymbol = { it.property as KaPropertySymbol },
        getSuperCallableSymbol = { it.superProperty as KaPropertySymbol },
    )

    val changeVariableReturnTypeOnOverride = changeReturnTypeOnOverride<KaFirDiagnostic.VarTypeMismatchOnOverride>(
        getCallableSymbol = { it.variable as KaPropertySymbol },
        getSuperCallableSymbol = { it.superVariable as KaPropertySymbol },
    )

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun getActualType(ktType: KaType): KaType {
        val typeKind = ktType.functionTypeKind
        when (typeKind) {
            FunctionTypeKind.KFunction -> typeKind.nonReflectKind()
            FunctionTypeKind.KSuspendFunction -> typeKind.nonReflectKind()
            else -> null
        }?.let {
            val functionalType = ktType as KaFunctionType
            return buildClassType(it.numberedClassId((functionalType).arity)) {
                functionalType.parameterTypes.forEach { arg ->
                    argument(arg)
                }
                argument(functionalType.returnType)
            }
        }
        return ktType.approximateToSuperPublicDenotableOrSelf(true)
    }

    context(KaSession)
    private fun KtElement.returnType(candidateType: KaType): KaType {
        val (initializers, functionOrGetter) = when (this) {
            is KtNamedFunction -> listOfNotNull(this.initializer) to this
            is KtProperty -> listOfNotNull(this.initializer, this.getter?.initializer) to this.getter
            is KtPropertyAccessor -> listOfNotNull(this.initializer) to this
            else -> return candidateType
        }
        val returnedExpressions = if (functionOrGetter != null) {
            val declarationSymbol = functionOrGetter.symbol
            functionOrGetter
                .collectDescendantsOfType<KtReturnExpression> { it.targetSymbol == declarationSymbol }
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
                (property?.getPropertyInitializerType() ?: returnExpr.expressionType)?.let { getActualType(it) }
            })
            if (!candidateType.isUnitType) {
                add(candidateType)
            }
        }.distinct()
        return if (returnTypes.isNotEmpty()) returnTypes.commonSupertype else candidateType
    }

    context(KaSession)
    private fun KtProperty.getPropertyInitializerType(): KaType? {
        val initializer = initializer
        return if (typeReference != null && initializer != null) {
            //copy property initializer to calculate initializer's type without property's declared type
            KtPsiFactory(project).createExpressionCodeFragment(initializer.text, this).getContentElement()?.expressionType
        } else null
    }

    val returnTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
            val element = diagnostic.targetFunction.psi as? KtElement
                ?: return@ModCommandBased emptyList()

            val declaration = element as? KtCallableDeclaration ?: (element as? KtPropertyAccessor)?.property
            ?: return@ModCommandBased emptyList()

            val actualType = diagnostic.actualType
            buildList {
                add(
                    UpdateTypeQuickFix(
                        declaration,
                        if (element is KtPropertyAccessor) TargetType.VARIABLE else TargetType.ENCLOSING_DECLARATION,
                        createTypeInfo(element.returnType(getActualType(actualType)))
                    )
                )
                addAll(
                    registerExpressionTypeFixes(diagnostic.psi, diagnostic.expectedType, actualType)
                )
            }
        }

    val returnTypeNullableTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
            val returnExpr = diagnostic.psi.parentOfType<KtReturnExpression>()
                ?: return@ModCommandBased emptyList()
            val declaration = returnExpr.targetSymbol?.psi as? KtCallableDeclaration
                ?: return@ModCommandBased emptyList()

            val withNullability = diagnostic.expectedType.withNullability(KaTypeNullability.NULLABLE)
            listOf(UpdateTypeQuickFix(declaration, TargetType.ENCLOSING_DECLARATION, createTypeInfo(declaration.returnType(withNullability))))
        }

    val initializerTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
            val declaration = diagnostic.psi as? KtProperty
                ?: return@ModCommandBased emptyList()

            registerVariableTypeFixes(declaration, getActualType(diagnostic.actualType))
        }

    val assignmentTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
            val expression = diagnostic.psi
            val assignment = expression.parent as? KtBinaryExpression
                ?: return@ModCommandBased emptyList()

            val declaration = (assignment.left as? KtNameReferenceExpression)?.mainReference?.resolve() as? KtProperty
                ?: return@ModCommandBased emptyList()

            if (isValReassignment(assignment)) {
                return@ModCommandBased emptyList()
            }

            val actualType = getActualType(diagnostic.actualType)
            val type = if (declaration.initializer?.isNull() == true) actualType.withNullability(KaTypeNullability.NULLABLE) else actualType
            buildList {
                if (declaration.typeReference == null) {
                    add(UpdateTypeQuickFix(declaration, TargetType.VARIABLE, createTypeInfo(declaration.returnType(type))))
                }
                addAll(registerExpressionTypeFixes(expression, diagnostic.expectedType, type))
            }
        }

    val typeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
            val expr = diagnostic.psi
            val property = expr.parent as? KtProperty
                ?: return@ModCommandBased emptyList()

            val actualType = property.getPropertyInitializerType() ?: diagnostic.actualType
            registerVariableTypeFixes(property, getActualType(actualType))
        }

    private fun KaSession.registerVariableTypeFixes(declaration: KtProperty, actualType: KaType): List<ModCommandAction> {
        val expectedType = declaration.returnType
        val expression = declaration.initializer ?: return emptyList()
        return buildList {
            add(UpdateTypeQuickFix(declaration, TargetType.VARIABLE, createTypeInfo(declaration.returnType(actualType))))
            addAll(registerExpressionTypeFixes(expression, expectedType, actualType))
        }
    }

    val parameterTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
            val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
            val actualType = getActualType(diagnostic.actualType)
            val expectedType = diagnostic.expectedType
            registerExpressionTypeFixes(expression, expectedType, actualType)
        }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.registerExpressionTypeFixes(
        expression: KtExpression,
        expectedType: KaType,
        actualType: KaType,
    ): List<ModCommandAction> {
        return buildList {
            var wrongPrimitiveLiteralFix: WrongPrimitiveLiteralFix? = null
            if (expression is KtConstantExpression && isNumberOrUNumberType(expectedType) && isNumberOrUNumberType(actualType)) {
                wrongPrimitiveLiteralFix = WrongPrimitiveLiteralFix.createIfAvailable(expression, expectedType, useSiteSession)
                addIfNotNull(wrongPrimitiveLiteralFix)
            }
            if (expectedType.isNumberOrCharType() && actualType.isNumberOrCharType()) {
                if (wrongPrimitiveLiteralFix == null) {
                    val elementContext = prepareNumberConversionElementContext(actualType, expectedType)
                    add(NumberConversionFix(expression, elementContext))
                    if (isRoundNumberFixAvailable(expression, expectedType)) {
                        val renderedExpectedType = expectedType.render(
                            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                            position = Variance.INVARIANT
                        )
                        add(RoundNumberFix(expression, renderedExpectedType))
                    }
                }
            }
        }
    }

    val componentFunctionReturnTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ComponentFunctionReturnTypeMismatch ->
            val entryWithWrongType =
                getDestructuringDeclarationEntryThatTypeMismatchComponentFunction(
                    diagnostic.componentFunctionName,
                    diagnostic.psi
                ) ?: return@ModCommandBased emptyList()

            buildList {
                add(UpdateTypeQuickFix(entryWithWrongType, TargetType.VARIABLE, createTypeInfo(diagnostic.destructingType)))

                val classSymbol = (diagnostic.psi.expressionType as? KaClassType)?.symbol as? KaDeclarationContainerSymbol ?: return@buildList
                val componentFunction = classSymbol.memberScope
                    .callables(diagnostic.componentFunctionName)
                    .firstOrNull()?.psi as? KtCallableDeclaration
                    ?: return@buildList
                add(UpdateTypeQuickFix(componentFunction, TargetType.CALLED_FUNCTION, createTypeInfo(diagnostic.expectedType)))
            }
        }

    private inline fun <reified DIAGNOSTIC : KaDiagnosticWithPsi<KtNamedDeclaration>> changeReturnTypeOnOverride(
        crossinline getCallableSymbol: (DIAGNOSTIC) -> KaCallableSymbol,
        crossinline getSuperCallableSymbol: (DIAGNOSTIC) -> KaCallableSymbol,
    ) = KotlinQuickFixFactory.ModCommandBased { diagnostic: DIAGNOSTIC ->
        val declaration = diagnostic.psi as? KtCallableDeclaration
            ?: return@ModCommandBased emptyList()

        val callable = getCallableSymbol(diagnostic)
        val superCallable = getSuperCallableSymbol(diagnostic)
        listOfNotNull(
            createChangeCurrentDeclarationQuickFix(superCallable, declaration),
            createChangeOverriddenFunctionQuickFix(callable, superCallable),
        )
    }

    private fun <PSI : KtCallableDeclaration> KaSession.createChangeCurrentDeclarationQuickFix(
        superCallable: KaCallableSymbol,
        declaration: PSI
    ): UpdateTypeQuickFix<PSI> = UpdateTypeQuickFix(declaration, TargetType.CURRENT_DECLARATION, createTypeInfo(superCallable.returnType))

    private fun KaSession.createChangeOverriddenFunctionQuickFix(
        callable: KaCallableSymbol,
        superCallable: KaCallableSymbol,
    ): UpdateTypeQuickFix<KtCallableDeclaration>? {
        val type = callable.returnType
        val singleMatchingOverriddenFunctionPsi = superCallable.psiSafe<KtCallableDeclaration>() ?: return null
        if (!singleMatchingOverriddenFunctionPsi.isWritable) return null
        return UpdateTypeQuickFix(singleMatchingOverriddenFunctionPsi, TargetType.BASE_DECLARATION, createTypeInfo(type))
    }

    private fun KaSession.createTypeInfo(ktType: KaType) = with(CallableReturnTypeUpdaterUtils.TypeInfo) {
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

@OptIn(KaExperimentalApi::class)
private fun KaSession.isValReassignment(assignment: KtBinaryExpression): Boolean {
    val left = assignment.left ?: return false
    return left.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).any {
        it is KaFirDiagnostic.ValReassignment
    }
}

fun KaSession.isNumberOrUNumberType(type: KaType): Boolean = isNumberType(type) || isUNumberType(type)
fun KaSession.isNumberType(type: KaType): Boolean = with(type) { isPrimitive && !isBooleanType && !isCharType }
fun KaSession.isUNumberType(type: KaType): Boolean = with(type) { isUByteType || isUShortType || isUIntType || isULongType }

private fun KaSession.isRoundNumberFixAvailable(expression: KtExpression, type: KaType): Boolean {
    val expressionType = expression.expressionType ?: return false
    return isLongOrInt(type) && isDoubleOrFloat(expressionType)
}

private fun KaSession.isLongOrInt(type: KaType): Boolean = type.isLongType || type.isIntType
private fun KaSession.isDoubleOrFloat(type: KaType): Boolean = type.isDoubleType || type.isFloatType