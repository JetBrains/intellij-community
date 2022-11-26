// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isKFunctionType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.reflectToRegularFunctionType
import org.jetbrains.kotlin.idea.util.approximateWithResolvableType
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isNull
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils.descriptorToDeclaration
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.*
import org.jetbrains.kotlin.resolve.constants.IntegerValueTypeConstant
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.types.CommonSupertypes
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.TypeUtils.NO_EXPECTED_TYPE
import org.jetbrains.kotlin.types.isDefinitelyNotNullType
import org.jetbrains.kotlin.types.typeUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.*

//TODO: should use change signature to deal with cases of multiple overridden descriptors
class QuickFixFactoryForTypeMismatchError : KotlinIntentionActionsFactory() {

    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val actions = LinkedList<IntentionAction>()

        val file = diagnostic.psiFile as KtFile
        val context = file.analyzeWithContent()

        val diagnosticElement = diagnostic.psiElement
        if (diagnosticElement !is KtExpression) {
            // INCOMPATIBLE_TYPES may be reported not only on expressions, but also on types.
            // We ignore such cases here.
            if (diagnostic.factory != Errors.INCOMPATIBLE_TYPES) {
                LOG.error("Unexpected element: " + diagnosticElement.text)
            }
            return emptyList()
        }

        val expectedType: KotlinType
        val expressionType: KotlinType?
        when (diagnostic.factory) {
            Errors.TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.TYPE_MISMATCH.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }
            Errors.TYPE_MISMATCH_WARNING -> {
                val diagnosticWithParameters = Errors.TYPE_MISMATCH_WARNING.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }
            Errors.NULL_FOR_NONNULL_TYPE -> {
                val diagnosticWithParameters = Errors.NULL_FOR_NONNULL_TYPE.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = expectedType.makeNullable()
            }
            Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }
            Errors.CONSTANT_EXPECTED_TYPE_MISMATCH -> {
                val diagnosticWithParameters = Errors.CONSTANT_EXPECTED_TYPE_MISMATCH.cast(diagnostic)
                expectedType = diagnosticWithParameters.b
                expressionType = context.getType(diagnosticElement)
            }
            Errors.SIGNED_CONSTANT_CONVERTED_TO_UNSIGNED -> {
                val constantValue = context[BindingContext.COMPILE_TIME_VALUE, diagnosticElement]
                if (constantValue is IntegerValueTypeConstant) {
                    // Here we have unsigned type (despite really constant is signed)
                    expectedType = constantValue.getType(NO_EXPECTED_TYPE)
                    val signedConstantValue = with(IntegerValueTypeConstant) {
                        constantValue.convertToSignedConstant(diagnosticElement.findModuleDescriptor())
                    }
                    // And here we have signed type
                    expressionType = signedConstantValue.getType(NO_EXPECTED_TYPE)
                } else return emptyList()
            }
            ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS -> {
                val diagnosticWithParameters = ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.cast(diagnostic)
                expectedType = diagnosticWithParameters.a
                expressionType = diagnosticWithParameters.b
            }
            Errors.INCOMPATIBLE_TYPES -> {
                val diagnosticWithParameters = Errors.INCOMPATIBLE_TYPES.cast(diagnostic)
                expectedType = diagnosticWithParameters.b
                expressionType = diagnosticWithParameters.a
            }
            else -> {
                LOG.error("Unexpected diagnostic: " + DefaultErrorMessages.render(diagnostic))
                return emptyList()
            }
        }
        if (expressionType == null) {
            LOG.error("No type inferred: " + diagnosticElement.text)
            return emptyList()
        }

        if (diagnosticElement is KtStringTemplateExpression &&
            expectedType.isChar() &&
            ConvertStringToCharLiteralFix.isApplicable(diagnosticElement)
        ) {
            actions.add(ConvertStringToCharLiteralFix(diagnosticElement))
        }

        if (expressionType.isSignedOrUnsignedNumberType() && expectedType.isSignedOrUnsignedNumberType()) {
            var wrongPrimitiveLiteralFix: WrongPrimitiveLiteralFix? = null
            if (diagnosticElement is KtConstantExpression && !KotlinBuiltIns.isChar(expectedType)) {
                wrongPrimitiveLiteralFix = WrongPrimitiveLiteralFix(diagnosticElement, expectedType)
                actions.add(wrongPrimitiveLiteralFix)
            }
            actions.add(NumberConversionFix(diagnosticElement, expressionType, expectedType, wrongPrimitiveLiteralFix))
            actions.add(RoundNumberFix(diagnosticElement, expectedType, wrongPrimitiveLiteralFix))
        }

        if (KotlinBuiltIns.isCharSequenceOrNullableCharSequence(expectedType) || KotlinBuiltIns.isStringOrNullableString(expectedType)) {
            actions.add(AddToStringFix(diagnosticElement, false))
            if (expectedType.isMarkedNullable && expressionType.isMarkedNullable) {
                actions.add(AddToStringFix(diagnosticElement, true))
            }
        }

        val convertKClassToClassFix = ConvertKClassToClassFix.create(file, expectedType, expressionType, diagnosticElement)
        if (convertKClassToClassFix != null) {
            actions.add(convertKClassToClassFix)
        }

        if (expectedType.isInterface()) {
            val expressionTypeDeclaration = expressionType.constructor.declarationDescriptor?.let {
                descriptorToDeclaration(it)
            } as? KtClassOrObject
            if (expressionTypeDeclaration != null && expectedType != TypeUtils.makeNotNullable(expressionType)) {
                actions.add(LetImplementInterfaceFix(expressionTypeDeclaration, expectedType, expressionType))
            }
        }

        actions.addAll(WrapWithCollectionLiteralCallFix.create(expectedType, expressionType, diagnosticElement))

        ConvertCollectionFix.getConversionTypeOrNull(expressionType, expectedType)?.let {
            actions.add(ConvertCollectionFix(diagnosticElement, it))
        }

        fun KtExpression.getTopMostQualifiedForSelectorIfAny(): KtExpression {
            var qualifiedOrThis = this
            do {
                val element = qualifiedOrThis
                qualifiedOrThis = element.getQualifiedExpressionForSelectorOrThis()
            } while (qualifiedOrThis !== element)
            return qualifiedOrThis
        }

        // We don't want to cast a cast or type-asserted expression.
        if (diagnostic.factory != Errors.SIGNED_CONSTANT_CONVERTED_TO_UNSIGNED &&
            diagnosticElement !is KtBinaryExpressionWithTypeRHS &&
            diagnosticElement.parent !is KtBinaryExpressionWithTypeRHS
        ) {
            actions.add(CastExpressionFix(diagnosticElement.getTopMostQualifiedForSelectorIfAny(), expectedType))
        }

        if (!expectedType.isMarkedNullable && TypeUtils.isNullableType(expressionType)) {
            val nullableExpected = expectedType.makeNullable()
            if (expressionType.isSubtypeOf(nullableExpected)) {
                val targetExpression = diagnosticElement.getTopMostQualifiedForSelectorIfAny()
                // With implicit receivers (e.g., inside a scope function),
                // NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS is reported on the callee, so we need
                // to explicitly check for nullable implicit receiver
                val checkCalleeExpression =
                    diagnostic.factory == ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS &&
                    targetExpression.parent?.safeAs<KtCallExpression>()?.calleeExpression == targetExpression
                getAddExclExclCallFix(targetExpression, checkCalleeExpression)?.let { actions.add(it) }
                if (expectedType.isBoolean()) {
                    actions.add(AddEqEqTrueFix(targetExpression))
                }
            }
        }

        fun <D : KtCallableDeclaration> addChangeTypeFix(
            callable: D,
            expressionType: KotlinType,
            createFix: (D, KotlinType) -> KotlinQuickFixAction<KtCallableDeclaration>
        ) {
            val scope = callable.getResolutionScope(context, callable.getResolutionFacade())
            val typeToInsert = expressionType.approximateWithResolvableType(scope, false)
            if (typeToInsert.isKFunctionType) {
                actions.add(createFix(callable, typeToInsert.reflectToRegularFunctionType()))
            }
            actions.add(createFix(callable, typeToInsert))
        }

        // Suggest replacing the parameter type `T` with its expected definitely non-nullable subtype `T & Any`.
        // Types that contain DNN types as arguments (like `(Mutable)Collection<T & Any>`) are currently not supported.
        // The "Change parameter type" action is generated only if the `DefinitelyNonNullableTypes` language feature is enabled:
        // if it is disabled, the `T & Any` intersection type is resolved as just `T`, so the fix would not have any effect.
        if (
            diagnosticElement is KtReferenceExpression &&
            expectedType.isDefinitelyNotNullType &&
            diagnosticElement.module?.languageVersionSettings?.supportsFeature(LanguageFeature.DefinitelyNonNullableTypes) == true
        ) {
            val descriptor = context[BindingContext.REFERENCE_TARGET, diagnosticElement]?.safeAs<CallableDescriptor>()
            when (val declaration = safeGetDeclaration(descriptor)) {
                is KtParameter -> {
                    // Check the parent parameter list to avoid creating actions for loop iterators
                    if (declaration.parent is KtParameterList) {
                        actions.add(ChangeParameterTypeFix(declaration, expectedType))
                    }
                }
                is KtProperty -> {
                    addChangeTypeFix(declaration, expectedType, ::ChangeVariableTypeFix)
                }
            }
        }

        // Property initializer type mismatch property type:
        val property = PsiTreeUtil.getParentOfType(diagnosticElement, KtProperty::class.java)
        if (property != null) {
            val getter = property.getter
            val initializer = property.initializer
            if (QuickFixBranchUtil.canEvaluateTo(initializer, diagnosticElement)
                || getter != null && QuickFixBranchUtil.canFunctionOrGetterReturnExpression(getter, diagnosticElement)
            ) {
                val returnType = property.returnType(expressionType, context)
                addChangeTypeFix(property, returnType, ::ChangeVariableTypeFix)
            }
        }

        val expressionParent = diagnosticElement.parent

        // Mismatch in returned expression:
        val function = if (expressionParent is KtReturnExpression)
            expressionParent.getTargetFunction(context)
        else
            PsiTreeUtil.getParentOfType(diagnosticElement, KtFunction::class.java, true)
        if (function is KtFunction && QuickFixBranchUtil.canFunctionOrGetterReturnExpression(function, diagnosticElement)) {
            val returnType = function.returnType(expressionType, context)
            addChangeTypeFix(function, returnType, ChangeCallableReturnTypeFix::ForEnclosing)
        }

        // Fixing overloaded operators:
        if (diagnosticElement is KtOperationExpression) {
            val resolvedCall = diagnosticElement.getResolvedCall(context)
            if (resolvedCall != null) {
                val declaration = getFunctionDeclaration(resolvedCall)
                if (declaration != null) {
                    actions.add(ChangeCallableReturnTypeFix.ForCalled(declaration, expectedType))
                }
            }
        }

        // Change function return type when TYPE_MISMATCH is reported on call expression:
        if (diagnosticElement is KtCallExpression) {
            val resolvedCall = diagnosticElement.getResolvedCall(context)
            if (resolvedCall != null) {
                val declaration = getFunctionDeclaration(resolvedCall)
                if (declaration != null) {
                    actions.add(ChangeCallableReturnTypeFix.ForCalled(declaration, expectedType))
                }
            }
        }

        // KT-10063: arrayOf() bounding single array element
        val annotationEntry = PsiTreeUtil.getParentOfType(diagnosticElement, KtAnnotationEntry::class.java)
        if (annotationEntry != null) {
            if (KotlinBuiltIns.isArray(expectedType) && expressionType.isSubtypeOf(expectedType.arguments[0].type)
                || KotlinBuiltIns.isPrimitiveArray(expectedType)
            ) {
                actions.add(AddArrayOfTypeFix(diagnosticElement, expectedType))
                actions.add(WrapWithArrayLiteralFix(diagnosticElement))
            }
        }

        diagnosticElement.getStrictParentOfType<KtParameter>()?.let {
            if (it.defaultValue == diagnosticElement) {
                actions.add(ChangeParameterTypeFix(it, expressionType))
            }
        }

        val resolvedCall = diagnosticElement.getParentResolvedCall(context, true)
        if (resolvedCall != null) {
            // to fix 'type mismatch' on 'if' branches
            // todo: the same with 'when'
            val parentIf = QuickFixBranchUtil.getParentIfForBranch(diagnosticElement)
            val argumentExpression = parentIf ?: diagnosticElement
            val valueArgument = resolvedCall.call.getValueArgumentForExpression(argumentExpression)
            if (valueArgument != null) {
                val correspondingParameterDescriptor = resolvedCall.getParameterForArgument(valueArgument)
                val correspondingParameter = safeGetDeclaration(correspondingParameterDescriptor) as? KtParameter
                val expressionFromArgument = valueArgument.getArgumentExpression()
                val valueArgumentType = when (diagnostic.factory) {
                    Errors.NULL_FOR_NONNULL_TYPE, Errors.SIGNED_CONSTANT_CONVERTED_TO_UNSIGNED -> expressionType
                    else -> expressionFromArgument?.let { context.getType(it) }
                }
                if (valueArgumentType != null) {
                    if (correspondingParameter != null) {
                        val callable = PsiTreeUtil.getParentOfType(correspondingParameter, KtCallableDeclaration::class.java, true)
                        val scope = callable?.getResolutionScope(context, callable.getResolutionFacade())
                        val typeToInsert = valueArgumentType.approximateWithResolvableType(scope, true)
                        actions.add(ChangeParameterTypeFix(correspondingParameter, typeToInsert))
                    }
                    val parameterVarargType = correspondingParameterDescriptor?.varargElementType
                    if ((parameterVarargType != null || resolvedCall.resultingDescriptor.fqNameSafe == FqName("kotlin.collections.mapOf"))
                        && KotlinBuiltIns.isArray(valueArgumentType)
                        && expressionType.arguments.isNotEmpty()
                        && expressionType.arguments[0].type.constructor == expectedType.constructor
                    ) {
                        actions.add(ChangeToUseSpreadOperatorFix(diagnosticElement))
                    }
                }
            }
        }
        return actions
    }

    private fun KtCallableDeclaration.returnType(candidateType: KotlinType, context: BindingContext): KotlinType {
        val (initializers, functionOrGetter) = when (this) {
            is KtNamedFunction -> listOfNotNull(this.initializer) to this
            is KtProperty -> listOfNotNull(this.initializer, this.getter?.initializer) to this.getter
            else -> return candidateType
        }
        val returnedExpressions = if (functionOrGetter != null) {
            val descriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, functionOrGetter]
            functionOrGetter
                .collectDescendantsOfType<KtReturnExpression> { it.getTargetFunctionDescriptor(context) == descriptor }
                .mapNotNull { it.returnedExpression }
                .plus(initializers)
        } else {
            initializers
        }.map { KtPsiUtil.safeDeparenthesize(it) }

        returnedExpressions.singleOrNull()?.let {
            if (it.isNull() || this.typeReference == null) return candidateType
        }

        val returnTypes = returnedExpressions.map { it.getActualType(this, context) ?: candidateType }.distinct()
        return returnTypes.singleOrNull() ?: CommonSupertypes.commonSupertype(returnTypes)
    }

    private fun KtExpression.getActualType(declaration: KtCallableDeclaration, context: BindingContext): KotlinType? {
        if (declaration.typeReference == null) return getType(context)
        val property = KtPsiFactory(project).createDeclarationByPattern<KtProperty>("val x = $0", this)
        val newContext = property.analyzeAsReplacement(this, context)
        return property.initializer?.getType(newContext)
    }

    companion object {
        private val LOG = Logger.getInstance(QuickFixFactoryForTypeMismatchError::class.java)

        private fun getFunctionDeclaration(resolvedCall: ResolvedCall<*>): KtFunction? {
            val result = safeGetDeclaration(resolvedCall.resultingDescriptor)
            if (result is KtFunction) {
                return result
            }
            return null
        }

        private fun safeGetDeclaration(descriptor: CallableDescriptor?): PsiElement? {
            //do not create fix if descriptor has more than one overridden declaration
            return if (descriptor == null || descriptor.overriddenDescriptors.size > 1) null else descriptorToDeclaration(descriptor)
        }
    }
}

