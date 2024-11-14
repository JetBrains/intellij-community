// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.LocalSearchScope
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveImportReference
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.intentions.InsertExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.LambdaToAnonymousFunctionIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.isInvokeOperator
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.*
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.NEW_DECLARATION_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.RECEIVER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.WAS_FUNCTION_LITERAL_ARGUMENT_KEY
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class CodeInliner (
    private val languageVersionSettings: LanguageVersionSettings,
    private val usageExpression: KtSimpleNameExpression?,
    private val bindingContext: BindingContext,
    private val resolvedCall: ResolvedCall<out CallableDescriptor>,
    private val callElement: KtElement,
    private val inlineSetter: Boolean,
    codeToInline: CodeToInline
): AbstractCodeInliner<KtElement, ValueParameterDescriptor, KotlinType, CallableDescriptor>(callElement, codeToInline) {

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
        receiver?.putCopyableUserData(USER_CODE_KEY, Unit)
        var receiverType = if (receiver != null) bindingContext.getType(receiver) else null

        if (receiver == null) {
            val receiverValue = if (descriptor.isExtension) resolvedCall.extensionReceiver else resolvedCall.dispatchReceiver
            if (receiverValue is ImplicitReceiver) {
                val resolutionScope = elementToBeReplaced.getResolutionScope(bindingContext, elementToBeReplaced.getResolutionFacade())
                receiver = receiverValue.asExpression(resolutionScope, psiFactory)
                receiverType = receiverValue.type
            }
        }

        receiver?.putCopyableUserData(RECEIVER_VALUE_KEY, Unit)

        if (receiver != null) {
            for (instanceExpression in codeToInline.collectDescendantsOfType<KtInstanceExpressionWithLabel> {
                // for this@ClassName we have only option to keep it as is (although it's sometimes incorrect but we have no other options)
                it is KtThisExpression && it.getCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY) == null && it.labelQualifier == null ||
                        it is KtSuperExpression && it.getCopyableUserData(CodeToInline.FAKE_SUPER_CALL_KEY) != null
            }) {
                codeToInline.replaceExpression(instanceExpression, receiver)
            }
        }

        val introduceValuesForParameters = processValueParameterUsages(callableForParameters)

        processTypeParameterUsages(
            callElement = resolvedCall.call.callElement as? KtCallElement,
            typeParameters = resolvedCall.resultingDescriptor.original.typeParameters,
            namer = { it.name },
            typeRetriever = { resolvedCall.typeArguments[it] },
            renderType = { IdeDescriptorRenderers.SOURCE_CODE.renderType(it) },
            isArrayType = { KotlinBuiltIns.isArray(it) },
            renderClassifier = {
                it.constructor.declarationDescriptor?.let {
                    IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(it)
                }
            }
        )

        val lexicalScopeElement = callElement.parentsWithSelf
            .takeWhile { it !is KtBlockExpression }
            .last() as KtElement

        val lexicalScope = lexicalScopeElement.getResolutionScope(lexicalScopeElement.analyze(BodyResolveMode.PARTIAL_FOR_COMPLETION))

        val importDescriptors = codeToInline.fqNamesToImport.mapNotNull { importPath ->
            val importDescriptor = file.resolveImportReference(importPath.importPath.fqName).firstOrNull() ?: return@mapNotNull null
            importPath to importDescriptor
        }

        if (elementToBeReplaced is KtSafeQualifiedExpression && receiverType?.isMarkedNullable != false) {
            wrapCodeForSafeCall(receiver!!, receiverType, elementToBeReplaced)
        } else if (callElement is KtBinaryExpression && callElement.operationToken == KtTokens.IDENTIFIER) {
            keepInfixFormIfPossible(importDescriptors.map { it.second })
        }

        codeToInline.convertToCallableReferenceIfNeeded(elementToBeReplaced)

        introduceVariablesForParameters(elementToBeReplaced, receiver, receiverType, introduceValuesForParameters)

        for ((importPath, importDescriptor) in importDescriptors) {
            ImportInsertHelper.getInstance(project).importDescriptor(file, importDescriptor, aliasName = importPath.importPath.alias)
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
            val pointers = range.filterIsInstance<KtElement>().map { it.createSmartPointer() }.toList()
            if (pointers.isEmpty()) {
                PsiChildRange.EMPTY
            }
            else {
                lexicalScope?.let { scope ->
                    val declarations = pointers.mapNotNull { pointer -> pointer.element?.takeIf { it.getCopyableUserData(NEW_DECLARATION_KEY) != null } as? KtNamedDeclaration }
                    if (declarations.isNotEmpty()) {
                        val endOfScope = pointers.last().element?.endOffset ?: error("Can't find the end of the scope")
                        renameDuplicates(declarations, scope, endOfScope)
                    }
                }
                InlinePostProcessor.postProcessInsertedCode(
                    pointers,
                    commentSaver
                )
            }
        })
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
                val newName = KotlinNameSuggester.suggestNameByName(oldName, validator)
                for (reference in ReferencesSearchScopeHelper.search(declaration, LocalSearchScope(declaration.parent))) {
                    if (reference.element.startOffset < endOfScope) {
                        reference.handleElementRename(newName)
                    }
                }

                declaration.nameIdentifier?.replace(psiFactory.createNameIdentifier(newName))
            }
        }
    }

    override fun CallableDescriptor.valueParameters(): List<ValueParameterDescriptor> = valueParameters

    override fun ValueParameterDescriptor.name(): Name = name

    override fun KtCallElement.insertExplicitTypeArgument() {
        if (InsertExplicitTypeArgumentsIntention.isApplicableTo(this, bindingContext)) {
            InsertExplicitTypeArgumentsIntention.createTypeArguments(this, bindingContext)?.let { typeArgumentList ->
                putCopyableUserData(USER_CODE_KEY, null)
                for (child in children) {
                    child.safeAs<KtElement>()?.putCopyableUserData(USER_CODE_KEY, Unit)
                }

                addAfter(typeArgumentList, calleeExpression)
            }
        }
    }

    override fun introduceValue(
        value: KtExpression,
        valueType: KotlinType?,
        usages: Collection<KtExpression>,
        expressionToBeReplaced: KtExpression,
        nameSuggestion: String?,
        safeCall: Boolean
    ) {
        codeToInline.introduceValue(value, valueType, usages, expressionToBeReplaced, nameSuggestion, safeCall)
    }

    private fun keepInfixFormIfPossible(importDescriptors: List<DeclarationDescriptor>) {
        if (codeToInline.statementsBefore.isNotEmpty()) return
        val dotQualified = codeToInline.mainExpression as? KtDotQualifiedExpression ?: return
        val receiver = dotQualified.receiverExpression
        if (receiver.getCopyableUserData(RECEIVER_VALUE_KEY) == null) return
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

    override fun argumentForParameter(parameter: ValueParameterDescriptor, callableDescriptor: CallableDescriptor): Argument? {
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
                val expression = valueArgument?.getArgumentExpression() ?: return null
                expression.putCopyableUserData(USER_CODE_KEY, Unit)
                val expressionType = bindingContext.getType(expression)
                val resultExpression = kotlin.run {
                    if (expression !is KtLambdaExpression) return@run null
                    if (valueArgument is LambdaArgument) {
                        expression.putCopyableUserData(WAS_FUNCTION_LITERAL_ARGUMENT_KEY, Unit)
                    }

                    if (!parameter.type.isExtensionFunctionType) return@run null
                    expression.functionLiteral.descriptor?.safeAs<FunctionDescriptor>()?.let { descriptor ->
                        LambdaToAnonymousFunctionIntention.Holder.convertLambdaToFunction(expression, descriptor)
                    }
                } ?: expression

                return Argument(resultExpression, expressionType, isNamed = valueArgument.isNamed())
            }

            is DefaultValueArgument -> {
                val (defaultValue, parameterUsages) = OptionalParametersHelper.defaultParameterValue(parameter, project) ?: return null

                for ((param, usages) in parameterUsages) {
                    usages.forEach { it.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, param.name) }
                }

                var defaultValueCopy = defaultValue.copied()

                // clean up user data in original
                defaultValue.forEachDescendantOfType<KtExpression> { it.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, null) }
                defaultValueCopy = expandTypeArgumentsInParameterDefault(defaultValue) ?: defaultValueCopy

                return Argument(defaultValueCopy, null/*TODO*/, isDefaultValue = true)
            }

            is VarargValueArgument -> {
                val arguments = resolvedArgument.arguments
                val single = arguments.singleOrNull()
                if (single?.getSpreadElement() != null) {
                    val expression = single.getArgumentExpression()!!
                    expression.putCopyableUserData(USER_CODE_KEY, Unit)
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
                        val argumentExpression = argument.getArgumentExpression()!!
                        argumentExpression.putCopyableUserData(USER_CODE_KEY, Unit)
                        appendExpression(argumentExpression)
                    }
                    appendFixedText(")")
                }
                return Argument(expression, parameter.type, isNamed = single?.isNamed() ?: false)
            }

            else -> error("Unknown argument type: $resolvedArgument")
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
}