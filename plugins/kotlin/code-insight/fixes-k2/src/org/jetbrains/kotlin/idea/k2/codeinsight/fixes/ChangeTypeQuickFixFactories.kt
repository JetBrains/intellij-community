// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.util.parentOfType
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.diagnostics.diagnostics
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.analysis.api.expressions.returnType
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.render
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleOrMultiCall
import org.jetbrains.kotlin.analysis.api.resolution.calls
import org.jetbrains.kotlin.analysis.api.resolution.function
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveCall
import org.jetbrains.kotlin.analysis.api.resolution.variable
import org.jetbrains.kotlin.analysis.api.scopes.memberScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaStandardTypeClassIds
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.approximateToDenotableSupertypeOrSelf
import org.jetbrains.kotlin.analysis.api.types.builtinTypes
import org.jetbrains.kotlin.analysis.api.types.classId
import org.jetbrains.kotlin.analysis.api.types.commonSupertype
import org.jetbrains.kotlin.analysis.api.types.restore
import org.jetbrains.kotlin.analysis.api.types.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.types.typeCreation.typeCreator
import org.jetbrains.kotlin.analysis.api.types.withNullability
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.CallableReturnTypeUpdaterUtils.updateType
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory.toFunctionType
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.hasTypeParametersOutOfScope
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.isResolvableInScope
import org.jetbrains.kotlin.idea.quickfix.ChangeTypeFixUtils
import org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithInitializer
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtOperationExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolution.KtResolvableCall
import org.jetbrains.kotlin.types.Variance

internal object ChangeTypeQuickFixFactories {
    enum class TargetType {
        CURRENT_DECLARATION,
        BASE_DECLARATION,
        ENCLOSING_DECLARATION,
        CALLED_FUNCTION,
        VARIABLE,
        PARAMETER,
    }

    private class UpdateTypeQuickFix<E : KtCallableDeclaration>(
        target: E,
        private val targetType: TargetType,
        private val typeInfo: CallableReturnTypeUpdaterUtils.TypeInfo,
    ) : KotlinPsiUpdateModCommandAction.ElementContextless<E>(target) {
        override fun getFamilyName(): String = KotlinBundle.message("fix.change.return.type.family")

        override fun getActionPresentation(context: ActionContext, element: E): Presentation {
            val name = getActionName(element, targetType, typeInfo)
            val priority =
                when(targetType) {
                    TargetType.BASE_DECLARATION,
                    TargetType.ENCLOSING_DECLARATION,
                    TargetType.CURRENT_DECLARATION -> PriorityAction.Priority.HIGH
                    else -> PriorityAction.Priority.NORMAL
                }
            return Presentation.of(name).withPriority(priority)
        }

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

    context(session: KaSession)
    private fun getActualType(ktType: KaType, position: KtElement): KaType {
        return ktType.toFunctionType() ?: ktType.approximateToDenotableSupertypeOrSelf(position)
    }

    context(_: KaSession)
    private fun KtElement.returnTypeWithCandidate(candidateType: KaType): KaType {
        val (initializers, functionOrGetter) = when (this) {
            is KtNamedFunction -> listOfNotNull(this.initializer) to this
            is KtProperty -> listOfNotNull(this.initializer, this.getter?.initializer) to this.getter
            is KtPropertyAccessor -> listOfNotNull(this.initializer) to this
            else -> return candidateType
        }
        val returnedExpressions = if (functionOrGetter != null) {
            val declarationSymbol = functionOrGetter.symbol
            functionOrGetter
                .collectDescendantsOfType<KtReturnExpression> { it.resolveSuccessfulSymbol() == declarationSymbol }
                .mapNotNull { it.returnedExpression }
                .plus(initializers)
        } else {
            initializers
        }.map { KtPsiUtil.safeDeparenthesize(it) }

        returnedExpressions.singleOrNull()?.let {
            if (it.isNull() || this is KtCallableDeclaration && this.typeReference == null || this is KtPropertyAccessor && this.typeReference == null) return candidateType
        }

        val property = this as? KtProperty
        val returnTypes = buildList {
            addAll(returnedExpressions.mapNotNull { returnExpr ->
                val propertyInitializerType = property?.getPropertyInitializerType()
                (propertyInitializerType ?: returnExpr.expressionType)?.let { getActualType(it, this@returnTypeWithCandidate) }
                    ?.takeUnless { it is KaErrorType }
            })
            if (candidateType.classId != KaStandardTypeClassIds.UNIT) {
                add(candidateType)
            }
        }.distinct()
        return if (returnTypes.isNotEmpty()) returnTypes.commonSupertype else candidateType
    }

    context(_: KaSession)
    private fun KtProperty.getPropertyInitializerType(): KaType? {
        val initializer = initializer
        return if (typeReference != null && initializer != null) initializer.typeWithoutExpectedType() else null
    }

    context(_: KaSession)
    private fun KtExpression.typeWithoutExpectedType(): KaType? {
        //copy the expression to calculate its type without the expected type from the context
        val newExpression = KtPsiFactory(project).createExpressionCodeFragment(text, this).getContentElement() ?: return null

        // A new expression has to be analyzed in the context of the newly created file. To go back to the outer session, a workaround with
        // converting a type to a pointer and back can be used
        val typePointer = analyze(newExpression) { newExpression.expressionType?.createPointer() }
        return typePointer?.restore()
    }

    val returnTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
            val element = diagnostic.targetFunction.psi as? KtElement
                ?: return@ModCommandBased emptyList()
            val diagnosticsPsi = diagnostic.psi

            val actualType = diagnostic.actualType
            val expectedType = diagnostic.expectedType

            buildList {
                addIfNotNull(createEnclosingDeclarationTypeFix(element, getActualType(actualType, element)))

                addIfNotNull(returnTypeMismatchReportedFromLambdaFix(element, actualType))

                addAll(
                    registerExpressionTypeFixes(diagnosticsPsi, expectedType, actualType)
                )
                addAll(
                    createTypeFixesForCalledDeclaration(diagnosticsPsi, expectedType, actualType)
                )
            }
        }

    context(_: KaSession)
    private fun createEnclosingDeclarationTypeFix(element: KtElement, candidateType: KaType): ModCommandAction? = when (element) {
        is KtFunctionLiteral -> null
        is KtCallableDeclaration ->
            UpdateTypeQuickFix(element, TargetType.ENCLOSING_DECLARATION, createTypeInfo(element.returnTypeWithCandidate(candidateType)))

        is KtPropertyAccessor ->
            UpdateTypeQuickFix(element.property, TargetType.VARIABLE, createTypeInfo(element.returnTypeWithCandidate(candidateType)))

        else -> null
    }

    context(session: KaSession)
    private fun returnTypeMismatchReportedFromLambdaFix(element: KtElement, actualType: KaType): ModCommandAction? {
        // Kotlin 2.2.x and earlier return required details about expected and action types in diagnostic
        if (element.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_2_3) return null

        // Required type has to be recreated since 2.3
        val expression =
            (element as? KtFunctionLiteral)?.parent as? KtLambdaExpression

        val enclosingDeclaration = expression?.parentOfType<KtDeclaration>()
        val lambdaDeclarationIsProperty = enclosingDeclaration is KtPropertyAccessor
        if (enclosingDeclaration !is KtCallableDeclaration && !lambdaDeclarationIsProperty) return null
        if (!expression.isReturnedFrom(enclosingDeclaration)) return null

        val newExpressionType =
            (expression.expressionType as? KaFunctionType)?.changeReturnType(actualType) ?:
            return null

        val candidateType = getActualType(newExpressionType, enclosingDeclaration)
        val ktType = enclosingDeclaration.returnTypeWithCandidate(candidateType)
        val typeInfo = createTypeInfo(ktType)

        val target =
            if (lambdaDeclarationIsProperty) enclosingDeclaration.property else enclosingDeclaration as KtCallableDeclaration

        val targetType =
            if (lambdaDeclarationIsProperty) TargetType.VARIABLE else TargetType.ENCLOSING_DECLARATION

        return UpdateTypeQuickFix(target, targetType, typeInfo)
    }

    context(_: KaSession)
    private fun KtLambdaExpression.isReturnedFrom(declaration: KtDeclaration): Boolean {
        val expression = getOutermostParenthesizerOrThis()
        return when (val parent = expression.parent) {
            is KtReturnExpression -> parent.resolveSuccessfulSymbol()?.psi == declaration
            is KtDeclarationWithInitializer -> parent == declaration && parent.initializer == expression
            else -> false
        }
    }

    context(session: KaSession)
    internal fun createTypeFixesForCalledDeclaration(
        expression: KtExpression,
        expectedType: KaType,
        actualType: KaType
    ): List<ModCommandAction> {
        val resolvedCall = (expression as? KtResolvableCall)?.resolveSuccessfulCall() ?: return emptyList()

        if (!isResolvableInScope(expectedType, expression)) return emptyList()
        return buildList {
            addIfNotNull(createUpdateTypeFixForCalledFunction(resolvedCall, expectedType))
            addAll(createUpdateTypeFixesForCalledVariable(resolvedCall, expression, actualType, expectedType))
        }
    }

    context(_: KaSession)
    private fun KaCallableSymbol.isSafeForChangeTypeFix(): Boolean {
        // It's not safe to create a fix if the symbol has more than one overridden declaration
        return this.allOverriddenSymbols.toSet().size <= 1
    }

    context(session: KaSession)
    private fun createUpdateTypeFixForCalledFunction(
        resolvedCall: KaSimpleOrMultiCall,
        expectedType: KaType
    ): UpdateTypeQuickFix<KtCallableDeclaration>? {
        val functionSymbol = resolvedCall.function?.symbol ?: return null
        // We can't change the constructor type
        if (functionSymbol is KaConstructorSymbol || functionSymbol is KaSamConstructorSymbol) return null
        if (!functionSymbol.isSafeForChangeTypeFix()) return null
        val calledFunction = functionSymbol.psi as? KtCallableDeclaration ?: return null
        return UpdateTypeQuickFix(calledFunction, TargetType.CALLED_FUNCTION, createTypeInfo(expectedType))
    }

    context(session: KaSession)
    private fun KaFunctionType.changeReturnType(newReturnType: KaType) : KaFunctionType {
        val type = this
        // TODO: could be simplified when KT-85037 is fixed
        return typeCreator.functionType {
            receiverType = type.receiverType
            for (parameterType in type.parameterTypes) {
                valueParameter(null, parameterType)
            }
            returnType = newReturnType
        }
    }


    context(session: KaSession)
    private fun createUpdateTypeFixesForCalledVariable(
        resolvedCall: KaSimpleOrMultiCall,
        expression: KtExpression,
        actualType: KaType,
        expectedType: KaType
    ): List<ModCommandAction> {
        val variableSymbol = resolvedCall.variable?.symbol ?: return emptyList()
        if (!variableSymbol.isSafeForChangeTypeFix()) return emptyList()
        val calledVariable = variableSymbol.psi as? KtProperty ?: return emptyList()
        return registerVariableTypeFixes(calledVariable, getActualType(actualType, expression), expectedType)
    }

    val returnTypeNullableTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
            val returnExpr = diagnostic.psi.parentOfType<KtReturnExpression>()
                ?: return@ModCommandBased emptyList()
            val declaration = returnExpr.resolveSuccessfulSymbol()?.psi as? KtCallableDeclaration
                ?: return@ModCommandBased emptyList()

            val withNullability = diagnostic.expectedType.withNullability(isMarkedNullable = true)
            listOf(
                UpdateTypeQuickFix(
                    declaration,
                    TargetType.ENCLOSING_DECLARATION,
                    createTypeInfo(declaration.returnTypeWithCandidate(withNullability))
                )
            )
        }

    val returnTypeRequired =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnInFunctionWithExpressionBody ->
            createRequireReturnTypeFix(diagnostic.psi)
        }

    val returnTypeRequiredWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnInFunctionWithExpressionBodyWarning ->
            createRequireReturnTypeFix(diagnostic.psi)
        }

    val returnTypeRequiredWithImplicitType =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnInFunctionWithExpressionBodyAndImplicitType ->
            createRequireReturnTypeFix(diagnostic.psi)
        }

    context(session: KaSession)
    private fun createRequireReturnTypeFix(returnExpr: KtReturnExpression): List<ModCommandAction> {
        val psi = returnExpr.resolveSuccessfulSymbol()?.psi
        val declaration = psi as? KtCallableDeclaration ?: (psi as? KtPropertyAccessor)?.property
            ?: return emptyList()

        val expressionType = returnExpr.returnedExpression?.expressionType ?: builtinTypes.unit
        return listOf(
            UpdateTypeQuickFix(
                declaration,
                TargetType.ENCLOSING_DECLARATION,
                createTypeInfo(declaration.returnTypeWithCandidate(expressionType))
            )
        )
    }

    val initializerTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InitializerTypeMismatch ->
            val declaration = diagnostic.psi as? KtProperty
                ?: return@ModCommandBased emptyList()

            val expectedType = diagnostic.expectedType
            val actualType = diagnostic.actualType
            val type = declaration.getPropertyInitializerType() ?: actualType
            val newActualType = getActualType(type, declaration)
            if (newActualType.semanticallyEquals(expectedType)) {
                return@ModCommandBased emptyList()
            }

            registerVariableTypeFixes(declaration, newActualType, expectedType)
        }

    val assignmentTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssignmentTypeMismatch ->
            val expression = diagnostic.expression
            val assignment = diagnostic.assignmentExpression ?: return@ModCommandBased emptyList()

            val declaration = (assignment.left as? KtNameReferenceExpression)?.mainReference?.resolve() as? KtProperty
                ?: return@ModCommandBased emptyList()

            if (isValReassignment(assignment)) {
                return@ModCommandBased emptyList()
            }

            val actualType = getActualType(diagnostic.actualType, expression)
            val type = if (declaration.initializer?.isNull() == true) actualType.withNullability(isMarkedNullable = true) else actualType
            buildList {
                if (declaration.typeReference == null) {
                    add(UpdateTypeQuickFix(declaration, TargetType.VARIABLE, createTypeInfo(declaration.returnTypeWithCandidate(type))))
                }
                val expectedType = diagnostic.expectedType
                if (!expectedType.semanticallyEquals(actualType)) {
                    addAll(registerExpressionTypeFixes(expression, expectedType, type))
                }
            }
        }

    val typeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeMismatch ->
            val expr = diagnostic.psi
            val property = expr.parent as? KtProperty
                ?: return@ModCommandBased emptyList()

            val actualType = getActualType(property.getPropertyInitializerType() ?: diagnostic.actualType, property)
            val expectedType = diagnostic.expectedType
            if (expectedType.semanticallyEquals(actualType)) {
                return@ModCommandBased emptyList()
            }
            registerVariableTypeFixes(property, actualType, expectedType)
        }

    val unresolvedCollectionLiteral =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedCollectionLiteral ->
            val literal = diagnostic.psi
            val expression = literal.getOutermostParenthesizerOrThis()
            when (val parent = expression.parent) {
                is KtProperty -> {
                    if (parent.initializer !== expression) return@ModCommandBased emptyList()

                    val initializerType = parent.getPropertyInitializerType() ?: return@ModCommandBased emptyList()
                    val actualType = getActualType(initializerType, parent)
                    if (actualType is KaErrorType) return@ModCommandBased emptyList()

                    registerVariableTypeFixes(parent, actualType, diagnostic.incompatibleBound)
                }

                is KtValueArgument -> createParameterTypeFix(literal, expression)

                is KtReturnExpression -> createReturnTypeFix(literal, parent)

                else -> emptyList()
            }
        }

    context(session: KaSession)
    private fun createReturnTypeFix(
        literal: KtCollectionLiteralExpression,
        returnExpression: KtReturnExpression,
    ): List<ModCommandAction> {
        val element = returnExpression.resolveSuccessfulSymbol()?.psi as? KtElement ?: return emptyList()

        val literalType = literal.typeWithoutExpectedType() ?: return emptyList()
        val actualType = getActualType(literalType, element)
        if (actualType is KaErrorType) return emptyList()
        if (!isResolvableInScope(actualType, element) || hasTypeParametersOutOfScope(actualType, element)) return emptyList()

        return listOfNotNull(createEnclosingDeclarationTypeFix(element, actualType))
    }

    context(session: KaSession)
    private fun createParameterTypeFix(
        literal: KtCollectionLiteralExpression,
        argument: KtExpression,
    ): List<ModCommandAction> {
        val callElement = argument.parentOfType<KtCallElement>() ?: return emptyList()
        val functionCall = (callElement as? KtResolvableCall)?.tryResolveCall()?.calls?.singleOrNull()?.function
            ?: return emptyList()
        if (functionCall.symbol.allOverriddenSymbols.any()) return emptyList()

        val parameterSymbol = functionCall.valueArgumentMapping[argument]?.symbol ?: return emptyList()
        if (parameterSymbol.isVararg) return emptyList()
        val parameter = parameterSymbol.psi as? KtParameter ?: return emptyList()
        if (!parameter.isWritable) return emptyList()

        val literalType = literal.typeWithoutExpectedType() ?: return emptyList()
        val actualType = getActualType(literalType, parameter)
        if (actualType is KaErrorType) return emptyList()
        if (!isResolvableInScope(actualType, parameter) || hasTypeParametersOutOfScope(actualType, parameter)) return emptyList()

        return listOf(UpdateTypeQuickFix(parameter, TargetType.PARAMETER, createTypeInfo(actualType)))
    }

    context(session: KaSession)
    private fun registerVariableTypeFixes(
        declaration: KtProperty,
        actualType: KaType,
        expectedTypeFromDiagnostics: KaType
    ): List<ModCommandAction> {
        if (!isResolvableInScope(expectedTypeFromDiagnostics, declaration)) return emptyList()
        val expectedTypeFromDeclaration = declaration.returnType
        val expression = declaration.initializer ?: return emptyList()
        return buildList {
            val typeToCreateTypeInfoFrom = if (!expectedTypeFromDeclaration.semanticallyEquals(actualType)) {
                declaration.returnTypeWithCandidate(actualType)
            } else {
                expectedTypeFromDiagnostics
            }
            add(UpdateTypeQuickFix(declaration, TargetType.VARIABLE, createTypeInfo(typeToCreateTypeInfoFrom)))
            addAll(registerExpressionTypeFixes(expression, expectedTypeFromDeclaration, actualType))
        }
    }

    val parameterTypeMismatch =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
            val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
            val actualType = getActualType(diagnostic.actualType, expression)
            val expectedType = diagnostic.expectedType
            if (actualType.semanticallyEquals(expectedType)) {
                emptyList()
            } else {
                registerExpressionTypeFixes(expression, expectedType, actualType)
            }
        }

    context(session: KaSession)
    private fun registerExpressionTypeFixes(
        expression: KtExpression,
        expectedType: KaType,
        actualType: KaType,
    ): List<ModCommandAction> {
        return buildList {
            var wrongPrimitiveLiteralFix: WrongPrimitiveLiteralFix? = null
            if (expression is KtConstantExpression && isNumberOrUNumberType(expectedType) && isNumberOrUNumberType(actualType)) {
                wrongPrimitiveLiteralFix = WrongPrimitiveLiteralFix.createIfAvailable(expression, expectedType)
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
            // Fixing overloaded operators
            if (expression is KtOperationExpression) {
                val resolvedCall = (expression as? KtResolvableCall)?.resolveSuccessfulCall()
                resolvedCall?.let { addIfNotNull(createUpdateTypeFixForCalledFunction(it, expectedType)) }
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

                val classSymbol =
                    (diagnostic.psi.expressionType as? KaClassType)?.symbol as? KaDeclarationContainerSymbol ?: return@buildList
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

    context(session: KaSession)
    private fun <PSI : KtCallableDeclaration> createChangeCurrentDeclarationQuickFix(
        superCallable: KaCallableSymbol,
        declaration: PSI
    ): UpdateTypeQuickFix<PSI> = UpdateTypeQuickFix(
        declaration,
        TargetType.CURRENT_DECLARATION,
        createTypeInfo(superCallable.returnType)
    )

    context(session: KaSession)
    private fun createChangeOverriddenFunctionQuickFix(
        callable: KaCallableSymbol,
        superCallable: KaCallableSymbol,
    ): UpdateTypeQuickFix<KtCallableDeclaration>? {
        val type = callable.returnType
        val singleMatchingOverriddenFunctionPsi = superCallable.psiSafe<KtCallableDeclaration>() ?: return null
        if (!singleMatchingOverriddenFunctionPsi.isWritable) return null
        return UpdateTypeQuickFix(singleMatchingOverriddenFunctionPsi, TargetType.BASE_DECLARATION, createTypeInfo(type))
    }

    context(session: KaSession)
    private fun createTypeInfo(ktType: KaType): CallableReturnTypeUpdaterUtils.TypeInfo =
        with(CallableReturnTypeUpdaterUtils.TypeInfo) {
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

    val incompatibleTypes = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IncompatibleTypes ->
        val expression = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()
        createTypeFixesForCalledDeclaration(expression, expectedType = diagnostic.typeA, actualType = diagnostic.typeB)
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
                "'${containerName?.let { "$containerName." } ?: ""}${declaration.name}'"
            }

            TargetType.PARAMETER -> "'${declaration.name}'"
        }
    }

    private val KtCallableDeclaration.presentationForQuickfix: String?
        get() {
            val containerName = parentOfType<KtClassOrObject>()?.nameAsName?.takeUnless { it.isSpecial }
            return ChangeTypeFixUtils.functionOrConstructorParameterPresentation(this, containerName?.asString())
        }

    val implicitNothingPropertyTypeFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ImplicitNothingPropertyType ->
        createImplicitNothingTypeFix(diagnostic.psi as? KtProperty)
    }

    val implicitNothingReturnTypeFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ImplicitNothingReturnType ->
        createImplicitNothingTypeFix(diagnostic.psi as? KtFunction)
    }

    private fun createImplicitNothingTypeFix(callable: KtCallableDeclaration?): List<ModCommandAction> {
        if (callable == null) return emptyList()
        return listOf(
            UpdateTypeQuickFix(
                callable,
                TargetType.ENCLOSING_DECLARATION,
                CallableReturnTypeUpdaterUtils.TypeInfo(CallableReturnTypeUpdaterUtils.TypeInfo.NOTHING)
            )
        )
    }
}

context(session: KaSession)
private fun isValReassignment(assignment: KtBinaryExpression): Boolean {
    val left = assignment.left ?: return false
    return left.diagnostics().directOnly(true).any {
        it is KaFirDiagnostic.ValReassignment
    }
}

context(session: KaSession)
fun isNumberOrUNumberType(type: KaType): Boolean = isNumberType(type) || isUNumberType(type)
context(session: KaSession)
fun isNumberType(type: KaType): Boolean =
    with(type.classId) {
        this in KaStandardTypeClassIds.PRIMITIVES && this != KaStandardTypeClassIds.BOOLEAN && this != KaStandardTypeClassIds.CHAR
    }
context(session: KaSession)
fun isUNumberType(type: KaType): Boolean =
    with(type.classId) {
        this == StandardClassIds.UByte || this == StandardClassIds.UShort || this == StandardClassIds.UInt || this == StandardClassIds.ULong
    }

context(session: KaSession)
private fun isRoundNumberFixAvailable(expression: KtExpression, type: KaType): Boolean {
    val expressionType = expression.expressionType ?: return false
    return isLongOrInt(type) && isDoubleOrFloat(expressionType)
}

context(session: KaSession)
private fun isLongOrInt(type: KaType): Boolean =
    with(type.classId) {
        this == KaStandardTypeClassIds.LONG || this == KaStandardTypeClassIds.INT
    }
context(session: KaSession)
private fun isDoubleOrFloat(type: KaType): Boolean =
    with(type.classId) {
        this == KaStandardTypeClassIds.DOUBLE || this == KaStandardTypeClassIds.FLOAT
    }