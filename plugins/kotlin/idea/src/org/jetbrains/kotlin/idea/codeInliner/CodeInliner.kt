// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.inspections.RedundantLambdaOrAnonymousFunctionInspection
import org.jetbrains.kotlin.idea.inspections.RedundantUnitExpressionInspection
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlineAnonymousFunctionProcessor
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompileTimeConstantUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class CodeInliner<TCallElement : KtElement>(
    private val languageVersionSettings: LanguageVersionSettings,
    private val usageExpression: KtSimpleNameExpression?,
    private val bindingContext: BindingContext,
    private val resolvedCall: ResolvedCall<out CallableDescriptor>,
    private val callElement: TCallElement,
    private val inlineSetter: Boolean,
    codeToInline: CodeToInline
) {
    private val codeToInline = codeToInline.toMutable()
    private val project = callElement.project
    private val psiFactory = KtPsiFactory(project)

    fun doInline(): KtElement? {
        val descriptor = resolvedCall.resultingDescriptor
        val file = callElement.containingKtFile

        val qualifiedElement = if (callElement is KtExpression) {
            callElement.getQualifiedExpressionForSelector()
                ?: callElement.callableReferenceExpressionForReference()
                ?: callElement
        } else callElement
        val assignment = (qualifiedElement as? KtExpression)
            ?.getAssignmentByLHS()
            ?.takeIf { it.operationToken == KtTokens.EQ }
        val callableForParameters = if (assignment != null && descriptor is PropertyDescriptor)
            descriptor.setter?.takeIf { inlineSetter && it.hasBody() } ?: descriptor
        else
            descriptor
        val elementToBeReplaced = assignment.takeIf { callableForParameters is PropertySetterDescriptor } ?: qualifiedElement
        val commentSaver = CommentSaver(elementToBeReplaced, saveLineBreaks = true)

        // if the value to be inlined is not used and has no side effects we may drop it
        if (codeToInline.mainExpression != null
            && !codeToInline.alwaysKeepMainExpression
            && assignment == null
            && elementToBeReplaced is KtExpression
            && !elementToBeReplaced.isUsedAsExpression(bindingContext)
            && !codeToInline.mainExpression.shouldKeepValue(usageCount = 0)
            && elementToBeReplaced.getStrictParentOfType<KtAnnotationEntry>() == null
        ) {
            codeToInline.mainExpression?.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.let { commentHolder ->
                codeToInline.addExtraComments(CommentHolder(emptyList(), commentHolder.leadingComments + commentHolder.trailingComments))
            }

            codeToInline.mainExpression = null
        }

        var receiver = usageExpression?.receiverExpression()
        receiver?.marked(USER_CODE_KEY)
        var receiverType = if (receiver != null) bindingContext.getType(receiver) else null

        if (receiver == null) {
            val receiverValue = if (descriptor.isExtension) resolvedCall.extensionReceiver else resolvedCall.dispatchReceiver
            if (receiverValue is ImplicitReceiver) {
                val resolutionScope = elementToBeReplaced.getResolutionScope(bindingContext, elementToBeReplaced.getResolutionFacade())
                receiver = receiverValue.asExpression(resolutionScope, psiFactory)
                receiverType = receiverValue.type
            }
        }

        receiver?.mark(RECEIVER_VALUE_KEY)

        if (receiver != null) {
            for (instanceExpression in codeToInline.collectDescendantsOfType<KtInstanceExpressionWithLabel> {
                // for this@ClassName we have only option to keep it as is (although it's sometimes incorrect but we have no other options)
                it is KtThisExpression && !it[CodeToInline.SIDE_RECEIVER_USAGE_KEY] && it.labelQualifier == null ||
                        it is KtSuperExpression && it[CodeToInline.FAKE_SUPER_CALL_KEY]
            }) {
                codeToInline.replaceExpression(instanceExpression, receiver)
            }
        }

        val introduceValuesForParameters = processValueParameterUsages(callableForParameters)

        processTypeParameterUsages()

        val lexicalScopeElement = callElement.parentsWithSelf
            .takeWhile { it !is KtBlockExpression }
            .last() as KtElement

        val lexicalScope = lexicalScopeElement.getResolutionScope(lexicalScopeElement.analyze(BodyResolveMode.PARTIAL_FOR_COMPLETION))

        val importDescriptors = codeToInline.fqNamesToImport.mapNotNull { importPath ->
            val importDescriptor = file.resolveImportReference(importPath.fqName).firstOrNull() ?: return@mapNotNull null
            importPath to importDescriptor
        }

        if (elementToBeReplaced is KtSafeQualifiedExpression && receiverType?.isMarkedNullable != false) {
            wrapCodeForSafeCall(receiver!!, receiverType, elementToBeReplaced)
        } else if (callElement is KtBinaryExpression && callElement.operationToken == KtTokens.IDENTIFIER) {
            keepInfixFormIfPossible(importDescriptors.map { it.second })
        }

        codeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced)

        if (elementToBeReplaced is KtExpression) {
            if (receiver != null) {
                val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it[RECEIVER_VALUE_KEY] }
                if (receiver.shouldKeepValue(usageCount = thisReplaced.size)) {
                    codeToInline.introduceValue(receiver, receiverType, thisReplaced, elementToBeReplaced)
                }
            }

            for ((parameter, value, valueType) in introduceValuesForParameters) {
                val usagesReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it[PARAMETER_VALUE_KEY] == parameter }
                codeToInline.introduceValue(
                    value,
                    valueType,
                    usagesReplaced,
                    elementToBeReplaced,
                    nameSuggestion = parameter.name.asString()
                )
            }
        }

        for ((importPath, importDescriptor) in importDescriptors) {
            ImportInsertHelper.getInstance(project).importDescriptor(file, importDescriptor, aliasName = importPath.alias)
        }

        codeToInline.extraComments?.restoreComments(elementToBeReplaced)

        findAndMarkNewDeclarations()
        val replacementPerformer = when (elementToBeReplaced) {
            is KtExpression -> {
                if (descriptor.isInvokeOperator) {
                    val call = elementToBeReplaced.getPossiblyQualifiedCallExpression()
                    val callee = call?.calleeExpression
                    if (callee != null && callee.text != OperatorNameConventions.INVOKE.asString()) {
                        val receiverExpression = (codeToInline.mainExpression as? KtQualifiedExpression)?.receiverExpression
                        when {
                            elementToBeReplaced is KtCallExpression && receiverExpression is KtThisExpression ->
                                receiverExpression.replace(callee)
                            elementToBeReplaced is KtDotQualifiedExpression ->
                                receiverExpression?.replace(psiFactory.createExpressionByPattern("$0.$1", receiverExpression, callee))
                        }
                    }
                }
                ExpressionReplacementPerformer(codeToInline, elementToBeReplaced)
            }
            is KtAnnotationEntry -> AnnotationEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            is KtSuperTypeCallEntry -> SuperTypeCallEntryReplacementPerformer(codeToInline, elementToBeReplaced)
            else -> {
                assert(!canBeReplaced(elementToBeReplaced))
                error("Unsupported element")
            }
        }

        assert(canBeReplaced(elementToBeReplaced))
        return replacementPerformer.doIt(postProcessing = { range ->
            val newRange = postProcessInsertedCode(range, lexicalScope)
            if (!newRange.isEmpty) {
                commentSaver.restore(newRange)
            }
            newRange
        })
    }

    private fun KtElement.callableReferenceExpressionForReference(): KtCallableReferenceExpression? =
        parent.safeAs<KtCallableReferenceExpression>()?.takeIf { it.callableReference == callElement }

    private fun KtSimpleNameExpression.receiverExpression(): KtExpression? =
        getReceiverExpression() ?: parent.safeAs<KtCallableReferenceExpression>()?.receiverExpression

    private fun MutableCodeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced: KtElement) {
        if (elementToBeReplaced !is KtCallableReferenceExpression) return
        val qualified = mainExpression?.safeAs<KtQualifiedExpression>() ?: return
        val reference = qualified.callExpression?.calleeExpression ?: qualified.selectorExpression ?: return
        val callableReference  = if (elementToBeReplaced.receiverExpression == null) {
            psiFactory.createExpressionByPattern("::$0", reference)
        } else {
            psiFactory.createExpressionByPattern("$0::$1", qualified.receiverExpression, reference)
        }
        codeToInline.replaceExpression(qualified, callableReference)
    }

    private fun findAndMarkNewDeclarations() {
        for (it in codeToInline.statementsBefore) {
            if (it is KtNamedDeclaration) {
                it.mark(NEW_DECLARATION_KEY)
            }
        }
    }

    private fun renameDuplicates(
        declarations: List<KtNamedDeclaration>,
        lexicalScope: LexicalScope,
        endOfScope: Int,
    ) {
        val validator = CollectingNameValidator { !it.nameHasConflictsInScope(lexicalScope, languageVersionSettings) }
        for (declaration in declarations) {
            val oldName = declaration.name
            if (oldName != null && oldName.nameHasConflictsInScope(lexicalScope, languageVersionSettings)) {
                val newName = Fe10KotlinNameSuggester.suggestNameByName(oldName, validator)
                for (reference in ReferencesSearchScopeHelper.search(declaration, LocalSearchScope(declaration.parent))) {
                    if (reference.element.startOffset < endOfScope) {
                        reference.handleElementRename(newName)
                    }
                }

                declaration.nameIdentifier?.replace(psiFactory.createNameIdentifier(newName))
            }
        }
    }

    private fun processValueParameterUsages(descriptor: CallableDescriptor): Collection<IntroduceValueForParameter> {
        val introduceValuesForParameters = ArrayList<IntroduceValueForParameter>()

        // process parameters in reverse order because default values can use previous parameters
        for (parameter in descriptor.valueParameters.asReversed()) {
            val argument = argumentForParameter(parameter, descriptor) ?: continue

            val expression = argument.expression.apply {
                if (this is KtCallElement) {
                    insertExplicitTypeArgument()
                }

                put(PARAMETER_VALUE_KEY, parameter)
            }

            val parameterName = parameter.name
            val usages = codeToInline.collectDescendantsOfType<KtExpression> {
                it[CodeToInline.PARAMETER_USAGE_KEY] == parameterName
            }

            usages.forEach {
                val usageArgument = it.parent as? KtValueArgument
                if (argument.isNamed) {
                    usageArgument?.mark(MAKE_ARGUMENT_NAMED_KEY)
                }
                if (argument.isDefaultValue) {
                    usageArgument?.mark(DEFAULT_PARAMETER_VALUE_KEY)
                }

                codeToInline.replaceExpression(it, expression.copied())
            }

            if (expression.shouldKeepValue(usageCount = usages.size)) {
                introduceValuesForParameters.add(IntroduceValueForParameter(parameter, expression, argument.expressionType))
            }
        }

        return introduceValuesForParameters
    }

    private fun KtCallElement.insertExplicitTypeArgument() {
        if (InsertExplicitTypeArgumentsIntention.isApplicableTo(this, bindingContext)) {
            InsertExplicitTypeArgumentsIntention.createTypeArguments(this, bindingContext)?.let { typeArgumentList ->
                clear(USER_CODE_KEY)
                for (child in children) {
                    child.safeAs<KtElement>()?.mark(USER_CODE_KEY)
                }

                addAfter(typeArgumentList, calleeExpression)
            }
        }
    }

    private data class IntroduceValueForParameter(
        val parameter: ValueParameterDescriptor,
        val value: KtExpression,
        val valueType: KotlinType?
    )

    private fun processTypeParameterUsages() {
        val typeParameters = resolvedCall.resultingDescriptor.original.typeParameters

        val callElement = resolvedCall.call.callElement
        val callExpression = callElement as? KtCallElement
        val explicitTypeArgs = callExpression?.typeArgumentList?.arguments
        if (explicitTypeArgs != null && explicitTypeArgs.size != typeParameters.size) return

        for ((index, typeParameter) in typeParameters.withIndex()) {
            val parameterName = typeParameter.name
            val usages = codeToInline.collectDescendantsOfType<KtExpression> {
                it[CodeToInline.TYPE_PARAMETER_USAGE_KEY] == parameterName
            }

            val type = resolvedCall.typeArguments[typeParameter] ?: continue
            val typeElement = if (explicitTypeArgs != null) { // we use explicit type arguments if available to avoid shortening
                val explicitArgTypeElement = explicitTypeArgs[index].typeReference?.typeElement ?: continue
                explicitArgTypeElement.marked(USER_CODE_KEY)
            } else {
                psiFactory.createType(IdeDescriptorRenderers.SOURCE_CODE.renderType(type)).typeElement ?: continue
            }

            val typeClassifier = type.constructor.declarationDescriptor

            for (usage in usages) {
                val parent = usage.parent
                if (parent is KtClassLiteralExpression && typeClassifier != null) {
                    // for class literal ("X::class") we need type arguments only for kotlin.Array
                    val arguments =
                        if (typeElement is KtUserType && KotlinBuiltIns.isArray(type)) typeElement.typeArgumentList?.text.orEmpty()
                        else ""
                    codeToInline.replaceExpression(
                        usage, psiFactory.createExpression(
                            IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(typeClassifier) + arguments
                        )
                    )
                } else if (parent is KtUserType) {
                    parent.replace(typeElement)
                } else {
                    //TODO: tests for this?
                    codeToInline.replaceExpression(usage, psiFactory.createExpression(typeElement.text))
                }
            }
        }
    }

    private fun wrapCodeForSafeCall(receiver: KtExpression, receiverType: KotlinType?, expressionToBeReplaced: KtExpression) {
        if (codeToInline.statementsBefore.isEmpty()) {
            val qualified = codeToInline.mainExpression as? KtQualifiedExpression
            if (qualified != null) {
                if (qualified.receiverExpression[RECEIVER_VALUE_KEY]) {
                    if (qualified is KtSafeQualifiedExpression) return // already safe
                    val selector = qualified.selectorExpression
                    if (selector != null) {
                        codeToInline.mainExpression = psiFactory.createExpressionByPattern("$0?.$1", receiver, selector)
                        return
                    }
                }
            }
        }

        if (codeToInline.statementsBefore.isEmpty() || expressionToBeReplaced.isUsedAsExpression(bindingContext)) {
            val thisReplaced = codeToInline.collectDescendantsOfType<KtExpression> { it[RECEIVER_VALUE_KEY] }
            codeToInline.introduceValue(receiver, receiverType, thisReplaced, expressionToBeReplaced, safeCall = true)
        } else {
            codeToInline.mainExpression = psiFactory.buildExpression {
                appendFixedText("if (")
                appendExpression(receiver)
                appendFixedText("!=null) {")
                with(codeToInline) {
                    appendExpressionsFromCodeToInline(postfixForMainExpression = "\n")
                }

                appendFixedText("}")
            }

            codeToInline.statementsBefore.clear()
        }
    }

    private fun keepInfixFormIfPossible(importDescriptors: List<DeclarationDescriptor>) {
        if (codeToInline.statementsBefore.isNotEmpty()) return
        val dotQualified = codeToInline.mainExpression as? KtDotQualifiedExpression ?: return
        val receiver = dotQualified.receiverExpression
        if (!receiver[RECEIVER_VALUE_KEY]) return
        val call = dotQualified.selectorExpression as? KtCallExpression ?: return
        val nameExpression = call.calleeExpression as? KtSimpleNameExpression ?: return
        val functionDescriptor =
            importDescriptors.firstOrNull { it.name.asString() == nameExpression.text } as? FunctionDescriptor ?: return
        if (!functionDescriptor.isInfix) return
        val argument = call.valueArguments.singleOrNull() ?: return
        if (argument.isNamed()) return
        val argumentExpression = argument.getArgumentExpression() ?: return
        codeToInline.mainExpression = psiFactory.createExpressionByPattern("$0 ${nameExpression.text} $1", receiver, argumentExpression)
    }

    private fun KtExpression?.shouldKeepValue(usageCount: Int): Boolean {
        if (usageCount == 1) return false
        val sideEffectOnly = usageCount == 0

        return when (this) {
            is KtSimpleNameExpression -> false
            is KtQualifiedExpression -> receiverExpression.shouldKeepValue(usageCount) || selectorExpression.shouldKeepValue(usageCount)
            is KtUnaryExpression -> operationToken in setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS) ||
                    baseExpression.shouldKeepValue(usageCount)

            is KtStringTemplateExpression -> entries.any {
                if (sideEffectOnly) it.expression.shouldKeepValue(usageCount) else it is KtStringTemplateEntryWithExpression
            }

            is KtThisExpression, is KtSuperExpression, is KtConstantExpression -> false
            is KtParenthesizedExpression -> expression.shouldKeepValue(usageCount)
            is KtArrayAccessExpression -> !sideEffectOnly ||
                    arrayExpression.shouldKeepValue(usageCount) ||
                    indexExpressions.any { it.shouldKeepValue(usageCount) }

            is KtBinaryExpression -> !sideEffectOnly ||
                    operationToken == KtTokens.IDENTIFIER ||
                    left.shouldKeepValue(usageCount) ||
                    right.shouldKeepValue(usageCount)

            is KtIfExpression -> !sideEffectOnly ||
                    condition.shouldKeepValue(usageCount) ||
                    then.shouldKeepValue(usageCount) ||
                    `else`.shouldKeepValue(usageCount)

            is KtBinaryExpressionWithTypeRHS -> !(sideEffectOnly && left.isNull())
            is KtClassLiteralExpression -> false
            is KtCallableReferenceExpression -> false
            null -> false
            else -> true
        }
    }

    private class Argument(
        val expression: KtExpression,
        val expressionType: KotlinType?,
        val isNamed: Boolean = false,
        val isDefaultValue: Boolean = false
    )

    private fun argumentForParameter(parameter: ValueParameterDescriptor, callableDescriptor: CallableDescriptor): Argument? {
        if (callableDescriptor is PropertySetterDescriptor) {
            val valueAssigned = (callElement as? KtExpression)
                ?.getQualifiedExpressionForSelectorOrThis()
                ?.getAssignmentByLHS()
                ?.right ?: return null
            return Argument(valueAssigned, bindingContext.getType(valueAssigned))
        }

        when (val resolvedArgument = resolvedCall.valueArguments[parameter] ?: return null) {
            is ExpressionValueArgument -> {
                val valueArgument = resolvedArgument.valueArgument
                val expression = valueArgument?.getArgumentExpression()
                expression?.mark(USER_CODE_KEY) ?: return null
                val expressionType = bindingContext.getType(expression)
                val resultExpression = kotlin.run {
                    if (expression !is KtLambdaExpression) return@run null
                    if (valueArgument is LambdaArgument) {
                        expression.mark(WAS_FUNCTION_LITERAL_ARGUMENT_KEY)
                    }

                    if (!parameter.type.isExtensionFunctionType) return@run null
                    expression.functionLiteral.descriptor?.safeAs<FunctionDescriptor>()?.let { descriptor ->
                        LambdaToAnonymousFunctionIntention.convertLambdaToFunction(expression, descriptor)
                    }
                } ?: expression

                return Argument(resultExpression, expressionType, isNamed = valueArgument.isNamed())
            }

            is DefaultValueArgument -> {
                val (defaultValue, parameterUsages) = OptionalParametersHelper.defaultParameterValue(parameter, project) ?: return null

                for ((param, usages) in parameterUsages) {
                    usages.forEach { it.put(CodeToInline.PARAMETER_USAGE_KEY, param.name) }
                }

                val defaultValueCopy = defaultValue.copied()

                // clean up user data in original
                defaultValue.forEachDescendantOfType<KtExpression> { it.clear(CodeToInline.PARAMETER_USAGE_KEY) }

                return Argument(defaultValueCopy, null/*TODO*/, isDefaultValue = true)
            }

            is VarargValueArgument -> {
                val arguments = resolvedArgument.arguments
                val single = arguments.singleOrNull()
                if (single?.getSpreadElement() != null) {
                    val expression = single.getArgumentExpression()!!.marked(USER_CODE_KEY)
                    return Argument(expression, bindingContext.getType(expression), isNamed = single.isNamed())
                }

                val elementType = parameter.varargElementType!!
                val expression = psiFactory.buildExpression {
                    appendFixedText(arrayOfFunctionName(elementType))
                    appendFixedText("(")
                    for ((i, argument) in arguments.withIndex()) {
                        if (i > 0) appendFixedText(",")
                        if (argument.getSpreadElement() != null) {
                            appendFixedText("*")
                        }
                        appendExpression(argument.getArgumentExpression()!!.marked(USER_CODE_KEY))
                    }
                    appendFixedText(")")
                }
                return Argument(expression, parameter.type, isNamed = single?.isNamed() ?: false)
            }

            else -> error("Unknown argument type: $resolvedArgument")
        }
    }

    private fun postProcessInsertedCode(range: PsiChildRange, lexicalScope: LexicalScope?): PsiChildRange {
        val pointers = range.filterIsInstance<KtElement>().map { it.createSmartPointer() }.toList()
        if (pointers.isEmpty()) return PsiChildRange.EMPTY

        lexicalScope?.let { scope ->
            val declarations = pointers.mapNotNull { pointer -> pointer.element?.takeIf { it[NEW_DECLARATION_KEY] } as? KtNamedDeclaration }
            if (declarations.isNotEmpty()) {
                val endOfScope = pointers.last().element?.endOffset ?: error("Can't find the end of the scope")
                renameDuplicates(declarations, scope, endOfScope)
            }
        }

        for (pointer in pointers) {
            restoreComments(pointer)

            introduceNamedArguments(pointer)

            restoreFunctionLiteralArguments(pointer)

            //TODO: do this earlier
            dropArgumentsForDefaultValues(pointer)

            removeRedundantLambdasAndAnonymousFunctions(pointer)

            simplifySpreadArrayOfArguments(pointer)

            removeExplicitTypeArguments(pointer)

            removeRedundantUnitExpressions(pointer)
        }

        val shortenFilter = { element: PsiElement ->
            if (element[USER_CODE_KEY]) {
                ShortenReferences.FilterResult.SKIP
            } else {
                val thisReceiver = (element as? KtQualifiedExpression)?.receiverExpression as? KtThisExpression
                if (thisReceiver != null && thisReceiver[USER_CODE_KEY]) // don't remove explicit 'this' coming from user's code
                    ShortenReferences.FilterResult.GO_INSIDE
                else
                    ShortenReferences.FilterResult.PROCESS
            }
        }

        // can simplify to single call after KTIJ-646
        val newElements = pointers.mapNotNull {
            it.element?.let { element ->
                ShortenReferences { ShortenReferences.Options(removeThis = true) }.process(element, elementFilter = shortenFilter)
            }
        }

        for (element in newElements) {
            // clean up user data
            element.forEachDescendantOfType<KtExpression> {
                it.clear(CommentHolder.COMMENTS_TO_RESTORE_KEY)
                it.clear(USER_CODE_KEY)
                it.clear(CodeToInline.PARAMETER_USAGE_KEY)
                it.clear(CodeToInline.TYPE_PARAMETER_USAGE_KEY)
                it.clear(CodeToInline.FAKE_SUPER_CALL_KEY)
                it.clear(PARAMETER_VALUE_KEY)
                it.clear(RECEIVER_VALUE_KEY)
                it.clear(WAS_FUNCTION_LITERAL_ARGUMENT_KEY)
                it.clear(NEW_DECLARATION_KEY)
            }

            element.forEachDescendantOfType<KtValueArgument> {
                it.clear(MAKE_ARGUMENT_NAMED_KEY)
                it.clear(DEFAULT_PARAMETER_VALUE_KEY)
            }
        }

        return if (newElements.isEmpty()) PsiChildRange.EMPTY else PsiChildRange(newElements.first(), newElements.last())
    }

    private fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        for (function in element.collectDescendantsOfType<KtFunction>().asReversed()) {
            val call = RedundantLambdaOrAnonymousFunctionInspection.findCallIfApplicableTo(function)
            if (call != null) {
                KotlinInlineAnonymousFunctionProcessor.performRefactoring(call, editor = null)
            }
        }
    }

    private fun restoreComments(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtExpression> {
            it.getCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY)?.restoreComments(it)
        }
    }

    private fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtReferenceExpression> {
            if (RedundantUnitExpressionInspection.isRedundantUnit(it)) {
                it.delete()
            }
        }
    }

    private fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        val callsToProcess = LinkedHashSet<KtCallExpression>()
        element.forEachDescendantOfType<KtValueArgument> {
            if (it[MAKE_ARGUMENT_NAMED_KEY] && !it.isNamed()) {
                val callExpression = (it.parent as? KtValueArgumentList)?.parent as? KtCallExpression
                callsToProcess.addIfNotNull(callExpression)
            }
        }

        for (callExpression in callsToProcess) {
            val resolvedCall = callExpression.resolveToCall() ?: return
            if (!resolvedCall.isReallySuccess()) return

            val argumentsToMakeNamed = callExpression.valueArguments.dropWhile { !it[MAKE_ARGUMENT_NAMED_KEY] }
            for (argument in argumentsToMakeNamed) {
                if (argument.isNamed()) continue
                if (argument is KtLambdaArgument) continue
                val argumentMatch = resolvedCall.getArgumentMapping(argument) as ArgumentMatch
                val name = argumentMatch.valueParameter.name
                //TODO: not always correct for vararg's
                val newArgument = psiFactory.createArgument(argument.getArgumentExpression()!!, name, argument.getSpreadElement() != null)

                if (argument[DEFAULT_PARAMETER_VALUE_KEY]) {
                    newArgument.mark(DEFAULT_PARAMETER_VALUE_KEY)
                }

                argument.replace(newArgument)
            }
        }
    }

    private fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        val project = result.project
        val newBindingContext = result.analyze()
        val argumentsToDrop = ArrayList<ValueArgument>()

        // we drop only those arguments that added to the code from some parameter's default
        fun canDropArgument(argument: ValueArgument) = (argument as KtValueArgument)[DEFAULT_PARAMETER_VALUE_KEY]

        result.forEachDescendantOfType<KtCallElement> { callExpression ->
            val resolvedCall = callExpression.getResolvedCall(newBindingContext) ?: return@forEachDescendantOfType

            argumentsToDrop.addAll(OptionalParametersHelper.detectArgumentsToDropForDefaults(resolvedCall, project, ::canDropArgument))
        }

        for (argument in argumentsToDrop) {
            argument as KtValueArgument
            val argumentList = argument.parent as KtValueArgumentList
            argumentList.removeArgument(argument)
            if (argumentList.arguments.isEmpty()) {
                val callExpression = argumentList.parent as KtCallElement
                if (callExpression.lambdaArguments.isNotEmpty()) {
                    argumentList.delete()
                }
            }
        }
    }

    private fun arrayOfFunctionName(elementType: KotlinType): String {
        return when {
            KotlinBuiltIns.isInt(elementType) -> "kotlin.intArrayOf"
            KotlinBuiltIns.isLong(elementType) -> "kotlin.longArrayOf"
            KotlinBuiltIns.isShort(elementType) -> "kotlin.shortArrayOf"
            KotlinBuiltIns.isChar(elementType) -> "kotlin.charArrayOf"
            KotlinBuiltIns.isBoolean(elementType) -> "kotlin.booleanArrayOf"
            KotlinBuiltIns.isByte(elementType) -> "kotlin.byteArrayOf"
            KotlinBuiltIns.isDouble(elementType) -> "kotlin.doubleArrayOf"
            KotlinBuiltIns.isFloat(elementType) -> "kotlin.floatArrayOf"
            elementType.isError -> "kotlin.arrayOf"
            else -> "kotlin.arrayOf<" + IdeDescriptorRenderers.SOURCE_CODE.renderType(elementType) + ">"
        }
    }

    private fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        for (typeArgumentList in result.collectDescendantsOfType<KtTypeArgumentList>(canGoInside = { !it[USER_CODE_KEY] }).asReversed()) {
            if (RemoveExplicitTypeArgumentsIntention.isApplicableTo(typeArgumentList, approximateFlexible = true)) {
                typeArgumentList.delete()
            }
        }
    }

    private fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        //TODO: test for nested

        val argumentsToExpand = ArrayList<Pair<KtValueArgument, Collection<KtValueArgument>>>()

        result.forEachDescendantOfType<KtValueArgument>(canGoInside = { !it[USER_CODE_KEY] }) { argument ->
            if (argument.getSpreadElement() != null && !argument.isNamed()) {
                val argumentExpression = argument.getArgumentExpression() ?: return@forEachDescendantOfType
                val resolvedCall = argumentExpression.resolveToCall() ?: return@forEachDescendantOfType
                val callExpression = resolvedCall.call.callElement as? KtCallElement ?: return@forEachDescendantOfType
                if (CompileTimeConstantUtils.isArrayFunctionCall(resolvedCall)) {
                    argumentsToExpand.add(argument to callExpression.valueArgumentList?.arguments.orEmpty())
                }
            }
        }

        for ((argument, replacements) in argumentsToExpand) {
            argument.replaceByMultiple(replacements)
        }
    }

    private fun KtValueArgument.replaceByMultiple(arguments: Collection<KtValueArgument>) {
        val list = parent as KtValueArgumentList
        if (arguments.isEmpty()) {
            list.removeArgument(this)
        } else {
            var anchor = this
            for (argument in arguments) {
                anchor = list.addArgumentAfter(argument, anchor)
            }
            list.removeArgument(this)
        }
    }

    private fun restoreFunctionLiteralArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val expression = pointer.element ?: return
        val callExpressions = ArrayList<KtCallExpression>()

        expression.forEachDescendantOfType<KtExpression>(fun(expr) {
            if (!expr[WAS_FUNCTION_LITERAL_ARGUMENT_KEY]) return
            assert(expr.unpackFunctionLiteral() != null)

            val argument = expr.parent as? KtValueArgument ?: return
            if (argument is KtLambdaArgument) return
            val argumentList = argument.parent as? KtValueArgumentList ?: return
            if (argument != argumentList.arguments.last()) return
            val callExpression = argumentList.parent as? KtCallExpression ?: return
            if (callExpression.lambdaArguments.isNotEmpty()) return

            callExpression.resolveToCall() ?: return
            callExpressions.add(callExpression)
        })

        callExpressions.forEach {
            if (it.canMoveLambdaOutsideParentheses()) {
                it.moveFunctionLiteralOutsideParentheses()
            }
        }
    }

    private operator fun <T : Any> PsiElement.get(key: Key<T>): T? = getCopyableUserData(key)
    private operator fun PsiElement.get(key: Key<Unit>): Boolean = getCopyableUserData(key) != null
    private fun <T : Any> KtElement.clear(key: Key<T>) = putCopyableUserData(key, null)
    private fun <T : Any> KtElement.put(key: Key<T>, value: T) = putCopyableUserData(key, value)
    private fun KtElement.mark(key: Key<Unit>) = putCopyableUserData(key, Unit)

    private fun <T : KtElement> T.marked(key: Key<Unit>): T {
        putCopyableUserData(key, Unit)
        return this
    }

    companion object {
        // keys below are used on expressions
        private val USER_CODE_KEY = Key<Unit>("USER_CODE")
        private val PARAMETER_VALUE_KEY = Key<ValueParameterDescriptor>("PARAMETER_VALUE")
        private val RECEIVER_VALUE_KEY = Key<Unit>("RECEIVER_VALUE")
        private val WAS_FUNCTION_LITERAL_ARGUMENT_KEY = Key<Unit>("WAS_FUNCTION_LITERAL_ARGUMENT")
        private val NEW_DECLARATION_KEY = Key<Unit>("NEW_DECLARATION")

        // these keys are used on KtValueArgument
        private val MAKE_ARGUMENT_NAMED_KEY = Key<Unit>("MAKE_ARGUMENT_NAMED")
        private val DEFAULT_PARAMETER_VALUE_KEY = Key<Unit>("DEFAULT_PARAMETER_VALUE")

        fun canBeReplaced(element: KtElement): Boolean = when (element) {
            is KtExpression, is KtAnnotationEntry, is KtSuperTypeCallEntry -> true
            else -> false
        }
    }
}
