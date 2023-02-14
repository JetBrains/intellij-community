// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInliner.CommentHolder.CommentNode.Companion.mergeComments
import org.jetbrains.kotlin.idea.core.asExpression
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.InsertExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.SpecifyExplicitLambdaSignatureIntention
import org.jetbrains.kotlin.idea.references.canBeResolvedViaImport
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.isAnonymousFunction
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.typeUtil.unCapture
import org.jetbrains.kotlin.utils.addToStdlib.cast
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class CodeToInlineBuilder(
    private val targetCallable: CallableDescriptor,
    private val resolutionFacade: ResolutionFacade,
    private val originalDeclaration: KtDeclaration?,
    private val fallbackToSuperCall: Boolean = false,
) {
    private val psiFactory = KtPsiFactory(resolutionFacade.project)

    fun prepareCodeToInlineWithAdvancedResolution(
        bodyOrExpression: KtExpression,
        expressionMapper: (bodyOrExpression: KtExpression) -> Pair<KtExpression?, List<KtExpression>>?,
    ): CodeToInline? {
        val (mainExpression, statementsBefore) = expressionMapper(bodyOrExpression) ?: return null
        val codeToInline = prepareMutableCodeToInline(
            mainExpression = mainExpression,
            statementsBefore = statementsBefore,
            analyze = { it.analyze(BodyResolveMode.PARTIAL) },
            reformat = true,
        )

        val copyOfBodyOrExpression = bodyOrExpression.copied()

        // Body's expressions to be inlined contain related comments as a user data (see CommentHolder.CommentNode.Companion.mergeComments).
        // When inlining (with untouched declaration!) is reverted and called again expressions become polluted with duplicates (^ merge!).
        // Now that we copied required data it's time to clear the storage.
        codeToInline.expressions.forEach { it.putCopyableUserData(CommentHolder.COMMENTS_TO_RESTORE_KEY, null) }

        val (resultMainExpression, resultStatementsBefore) = expressionMapper(copyOfBodyOrExpression) ?: return null
        codeToInline.mainExpression = resultMainExpression
        codeToInline.statementsBefore.clear()
        codeToInline.statementsBefore.addAll(resultStatementsBefore)

        return codeToInline.toNonMutable()
    }

    private fun prepareMutableCodeToInline(
        mainExpression: KtExpression?,
        statementsBefore: List<KtExpression>,
        analyze: (KtExpression) -> BindingContext,
        reformat: Boolean,
    ): MutableCodeToInline {
        val alwaysKeepMainExpression =
            when (val descriptor = mainExpression?.getResolvedCall(analyze(mainExpression))?.resultingDescriptor) {
                is PropertyDescriptor -> descriptor.getter?.isDefault == false
                else -> false
            }

        val codeToInline = MutableCodeToInline(
            mainExpression,
            statementsBefore.toMutableList(),
            mutableSetOf(),
            alwaysKeepMainExpression,
            extraComments = null,
        )

        if (originalDeclaration != null) {
            saveComments(codeToInline, originalDeclaration)
        }

        insertExplicitTypeArguments(codeToInline, analyze)
        processReferences(codeToInline, analyze, reformat)
        removeContracts(codeToInline, analyze)

        when {
            mainExpression == null -> Unit
            mainExpression.isNull() -> targetCallable.returnType?.let { returnType ->
                codeToInline.addPreCommitAction(mainExpression) {
                    codeToInline.replaceExpression(
                        it,
                        psiFactory.createExpression(
                            "null as ${
                                IdeDescriptorRenderers.FQ_NAMES_IN_TYPES_WITH_NORMALIZER.renderType(returnType)
                            }"
                        ),
                    )
                }
            }

            else -> {
                val functionLiteralExpression = mainExpression.unpackFunctionLiteral(true)
                if (functionLiteralExpression != null) {
                    val functionLiteralParameterTypes = getParametersForFunctionLiteral(functionLiteralExpression, analyze)
                    if (functionLiteralParameterTypes != null) {
                        codeToInline.addPostInsertionAction(mainExpression) { inlinedExpression ->
                            addFunctionLiteralParameterTypes(functionLiteralParameterTypes, inlinedExpression)
                        }
                    }
                }
            }
        }

        return codeToInline
    }

    private fun removeContracts(codeToInline: MutableCodeToInline, analyze: (KtExpression) -> BindingContext) {
        for (statement in codeToInline.statementsBefore) {
            val context = analyze(statement)
            if (statement.getResolvedCall(context)?.resultingDescriptor?.fqNameOrNull()?.asString() == "kotlin.contracts.contract") {
                codeToInline.addPreCommitAction(statement) {
                    codeToInline.statementsBefore.remove(it)
                }
            }
        }
    }

    fun prepareCodeToInline(
        mainExpression: KtExpression?,
        statementsBefore: List<KtExpression>,
        analyze: (KtExpression) -> BindingContext,
        reformat: Boolean,
    ): CodeToInline = prepareMutableCodeToInline(mainExpression, statementsBefore, analyze, reformat).toNonMutable()

    private fun saveComments(codeToInline: MutableCodeToInline, contextDeclaration: KtDeclaration) {
        val bodyBlockExpression = contextDeclaration.safeAs<KtDeclarationWithBody>()?.bodyBlockExpression
        if (bodyBlockExpression != null) addCommentHoldersForStatements(codeToInline, bodyBlockExpression)
    }

    private fun addCommentHoldersForStatements(mutableCodeToInline: MutableCodeToInline, blockExpression: KtBlockExpression) {
        val expressions = mutableCodeToInline.expressions

        for ((indexOfIteration, commentHolder) in CommentHolder.extract(blockExpression).withIndex()) {
            if (commentHolder.isEmpty) continue

            if (expressions.isEmpty()) {
                mutableCodeToInline.addExtraComments(commentHolder)
            } else {
                val expression = expressions.elementAtOrNull(indexOfIteration)
                if (expression != null) {
                    expression.mergeComments(commentHolder)
                } else {
                    expressions.last().mergeComments(
                        CommentHolder(emptyList(), trailingComments = commentHolder.leadingComments + commentHolder.trailingComments)
                    )
                }
            }
        }
    }

    private fun getParametersForFunctionLiteral(
        functionLiteralExpression: KtLambdaExpression,
        analyze: (KtExpression) -> BindingContext
    ): String? {
        val context = analyze(functionLiteralExpression)
        val lambdaDescriptor = context.get(BindingContext.FUNCTION, functionLiteralExpression.functionLiteral)
        if (lambdaDescriptor == null ||
            ErrorUtils.containsErrorTypeInParameters(lambdaDescriptor) ||
            ErrorUtils.containsErrorType(lambdaDescriptor.returnType)
        ) return null

        return lambdaDescriptor.valueParameters.joinToString {
            it.name.render() + ": " + IdeDescriptorRenderers.SOURCE_CODE.renderType(it.type)
        }
    }

    private fun addFunctionLiteralParameterTypes(parameters: String, inlinedExpression: KtExpression) {
        val containingFile = inlinedExpression.containingKtFile
        val resolutionFacade = containingFile.getResolutionFacade()
        val lambdaExpr = inlinedExpression.unpackFunctionLiteral(true).sure {
            "can't find function literal expression for " + inlinedExpression.text
        }

        if (!needToAddParameterTypes(lambdaExpr, resolutionFacade)) return
        SpecifyExplicitLambdaSignatureIntention.applyWithParameters(lambdaExpr, parameters)
    }

    private fun needToAddParameterTypes(
        lambdaExpression: KtLambdaExpression,
        resolutionFacade: ResolutionFacade
    ): Boolean {
        val functionLiteral = lambdaExpression.functionLiteral
        val context = resolutionFacade.analyze(lambdaExpression, BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        return context.diagnostics.any { diagnostic ->
            val factory = diagnostic.factory
            val element = diagnostic.psiElement
            val hasCantInferParameter =
                factory == Errors.CANNOT_INFER_PARAMETER_TYPE && functionLiteral == element.parentsWithSelf.match(
                    KtParameter::class,
                    KtParameterList::class,
                    last = KtFunctionLiteral::class
                )
            val hasUnresolvedItOrThis = factory == Errors.UNRESOLVED_REFERENCE &&
                    element.text == "it" &&
                    element.getStrictParentOfType<KtFunctionLiteral>() == functionLiteral

            hasCantInferParameter || hasUnresolvedItOrThis
        }
    }

    private fun insertExplicitTypeArguments(
        codeToInline: MutableCodeToInline,
        analyze: (KtExpression) -> BindingContext,
    ) = codeToInline.forEachDescendantOfType<KtCallExpression> {
        val bindingContext = analyze(it)
        if (InsertExplicitTypeArgumentsIntention.isApplicableTo(it, bindingContext)) {
            val typeArguments = InsertExplicitTypeArgumentsIntention.createTypeArguments(it, bindingContext)!!
            codeToInline.addPreCommitAction(it) { callExpression ->
                callExpression.addAfter(typeArguments, callExpression.calleeExpression)
                callExpression.typeArguments.forEach { typeArgument ->
                    val reference = typeArgument.typeReference?.typeElement?.safeAs<KtUserType>()?.referenceExpression
                    reference?.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, Name.identifier(reference.text))
                }
            }
        }
    }

    private fun findDescriptorAndContext(
        expression: KtSimpleNameExpression,
        analyzer: (KtExpression) -> BindingContext
    ): Pair<BindingContext, DeclarationDescriptor>? {
        val currentContext = analyzer(expression)
        val descriptor = currentContext[BindingContext.SHORT_REFERENCE_TO_COMPANION_OBJECT, expression]
            ?: currentContext[BindingContext.REFERENCE_TARGET, expression]

        if (descriptor != null) return currentContext to descriptor
        return findCallableDescriptorAndContext(expression, analyzer)
    }

    private fun findCallableDescriptorAndContext(
        expression: KtSimpleNameExpression,
        analyzer: (KtExpression) -> BindingContext
    ): Pair<BindingContext, CallableDescriptor>? {
        val callExpression = expression.parent as? KtCallExpression ?: return null
        val context = analyzer(callExpression)
        return callExpression.getResolvedCall(context)?.resultingDescriptor?.let { context to it }
    }

    private fun findResolvedCall(
        expression: KtSimpleNameExpression,
        bindingContext: BindingContext,
        analyzer: (KtExpression) -> BindingContext,
    ): Pair<KtExpression, ResolvedCall<out CallableDescriptor>>? {
        return getResolvedCallIfReallySuccess(expression, bindingContext)?.let { expression to it }
            ?: expression.parent?.safeAs<KtCallExpression>()?.let { callExpression ->
                getResolvedCallIfReallySuccess(callExpression, analyzer(callExpression))?.let { callExpression to it }
            }
    }

    private fun getResolvedCallIfReallySuccess(
        expression: KtExpression,
        bindingContext: BindingContext
    ): ResolvedCall<out CallableDescriptor>? {
        return expression.getResolvedCall(bindingContext)?.takeIf { it.isReallySuccess() }
    }

    private fun processReferences(codeToInline: MutableCodeToInline, analyze: (KtExpression) -> BindingContext, reformat: Boolean) {
        val targetDispatchReceiverType = targetCallable.dispatchReceiverParameter?.value?.type?.unCapture()
        val targetExtensionReceiverType = targetCallable.extensionReceiverParameter?.value?.type?.unCapture()
        val isAnonymousFunction = originalDeclaration?.isAnonymousFunction == true
        val isAnonymousFunctionWithReceiver = isAnonymousFunction &&
                originalDeclaration.cast<KtNamedFunction>().receiverTypeReference != null

        fun getParameterName(parameter: ValueParameterDescriptor): Name = if (isAnonymousFunction) {
            val shift = if (isAnonymousFunctionWithReceiver) 2 else 1
            Name.identifier("p${parameter.index + shift}")
        } else {
            parameter.name
        }

        codeToInline.forEachDescendantOfType<KtSimpleNameExpression> { expression ->
            val parent = expression.parent
            if (parent is KtValueArgumentName || parent is KtCallableReferenceExpression) return@forEachDescendantOfType
            val (bindingContext, target) = findDescriptorAndContext(expression, analyze)
                ?: return@forEachDescendantOfType addFakeSuperReceiver(codeToInline, expression)

            //TODO: other types of references ('[]' etc)
            if (expression.canBeResolvedViaImport(target, bindingContext)) {
                val importableFqName = target.importableFqName
                if (importableFqName != null) {
                    val shortName = importableFqName.shortName()
                    val ktFile = expression.containingKtFile
                    val aliasName = if (shortName.asString() != expression.getReferencedName())
                        ktFile.findAliasByFqName(importableFqName)?.name?.let(Name::identifier)
                    else
                        null

                    val lexicalScope = ktFile.getResolutionScope(bindingContext, resolutionFacade)
                    val lookupName = lexicalScope.findClassifier(aliasName ?: shortName, NoLookupLocation.FROM_IDE)
                        ?.typeConstructor
                        ?.declarationDescriptor
                        ?.fqNameOrNull()

                    codeToInline.fqNamesToImport.add(
                        ImportPath(
                            fqName = lookupName ?: importableFqName,
                            isAllUnder = false,
                            alias = aliasName,
                        )
                    )
                }
            }

            val callableDescriptor = targetCallable.safeAs<ImportedFromObjectCallableDescriptor<*>>()?.callableFromObject ?: targetCallable
            val receiverExpression = expression.getReceiverExpression()
            if (receiverExpression != null &&
                parent is KtCallExpression &&
                target is ValueParameterDescriptor &&
                target.type.isExtensionFunctionType &&
                target.containingDeclaration == callableDescriptor
            ) {
                codeToInline.addPreCommitAction(parent) { callExpression ->
                    val qualifiedExpression = callExpression.parent as KtDotQualifiedExpression
                    val valueArgumentList = callExpression.getOrCreateValueArgumentList()
                    val newArgument = psiFactory.createArgument(qualifiedExpression.receiverExpression)
                    valueArgumentList.addArgumentBefore(newArgument, valueArgumentList.arguments.firstOrNull())
                    val newExpression = qualifiedExpression.replaced(callExpression)
                    if (qualifiedExpression in codeToInline) {
                        codeToInline.replaceExpression(qualifiedExpression, newExpression)
                    }
                }

                expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getParameterName(target))
            } else if (receiverExpression == null) {
                if (isAnonymousFunctionWithReceiver && target == callableDescriptor) {
                    // parent is [KtThisExpression]
                    parent.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getFirstParameterName())
                } else if (target is ValueParameterDescriptor && target.containingDeclaration == callableDescriptor) {
                    expression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getParameterName(target))
                } else if (target is TypeParameterDescriptor && target.containingDeclaration == callableDescriptor) {
                    expression.putCopyableUserData(CodeToInline.TYPE_PARAMETER_USAGE_KEY, target.name)
                }

                if (targetCallable !is ImportedFromObjectCallableDescriptor<*>) {
                    val (expressionToResolve, resolvedCall) = findResolvedCall(expression, bindingContext, analyze)
                        ?: return@forEachDescendantOfType

                    val receiver = if (resolvedCall.resultingDescriptor.isExtension)
                        resolvedCall.extensionReceiver
                    else
                        resolvedCall.dispatchReceiver

                    if (receiver is ImplicitReceiver) {
                        val resolutionScope = expression.getResolutionScope(bindingContext, resolutionFacade)
                        val receiverExpressionToInline = receiver.asExpression(resolutionScope, psiFactory)
                        val receiverType = receiver.type.unCapture()
                        val isSameReceiverType = receiverType == targetDispatchReceiverType || receiverType == targetExtensionReceiverType
                        val receiverIsUnnecessary =
                            (receiverExpressionToInline as? KtThisExpression)?.labelQualifier != null && isSameReceiverType
                        if (receiverExpressionToInline != null && !receiverIsUnnecessary) {
                            codeToInline.addPreCommitAction(expressionToResolve) { expr ->
                                val expressionToReplace = expr.parent as? KtCallExpression ?: expr
                                val replaced = codeToInline.replaceExpression(
                                    expressionToReplace,
                                    psiFactory.createExpressionByPattern(
                                        "$0.$1", receiverExpressionToInline, expressionToReplace,
                                        reformat = reformat
                                    )
                                ) as? KtQualifiedExpression

                                val thisExpression = replaced?.receiverExpression ?: return@addPreCommitAction
                                if (isAnonymousFunctionWithReceiver && receiverType == targetExtensionReceiverType) {
                                    thisExpression.putCopyableUserData(CodeToInline.PARAMETER_USAGE_KEY, getFirstParameterName())
                                } else if (!isSameReceiverType) {
                                    thisExpression.putCopyableUserData(CodeToInline.SIDE_RECEIVER_USAGE_KEY, Unit)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun addFakeSuperReceiver(codeToInline: MutableCodeToInline, expression: KtExpression) {
        if (!fallbackToSuperCall || expression !is KtNameReferenceExpression) return

        val parent = expression.parent
        val prevSiblingElementType = expression.getPrevSiblingIgnoringWhitespaceAndComments()?.elementType
        if (prevSiblingElementType != KtTokens.SAFE_ACCESS && prevSiblingElementType != KtTokens.DOT) {
            val expressionToReplace = if (parent is KtCallExpression) parent else expression
            codeToInline.addPreCommitAction(expressionToReplace) { referenceExpression ->
                val qualifierExpression = codeToInline.replaceExpression(
                    referenceExpression,
                    psiFactory.createExpressionByPattern("super.$0", referenceExpression),
                ) as? KtDotQualifiedExpression

                qualifierExpression?.receiverExpression?.putCopyableUserData(CodeToInline.FAKE_SUPER_CALL_KEY, Unit)
            }
        }
    }
}

private fun getFirstParameterName(): Name = Name.identifier("p1")
