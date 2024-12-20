// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util.psi.patternMatching

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange.Empty
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiUnificationResult.*
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.introduce.K1ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorEquivalenceForOverrides
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.util.getCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.isSafeCall
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.error.ErrorUtils
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import java.util.*

class UnifierParameter(
    val descriptor: DeclarationDescriptor,
    val expectedType: KotlinType?
)

/**
 * For the K2 Mode-specific version of this functionality, see [org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher].  
 */
class KotlinPsiUnifier(
    parameters: Collection<UnifierParameter> = Collections.emptySet(),
    val allowWeakMatches: Boolean = false
) {
    companion object {
        val DEFAULT = KotlinPsiUnifier()
    }

    private inner class Context(val originalTarget: KotlinPsiRange, val originalPattern: KotlinPsiRange) {
        val patternContext: BindingContext = originalPattern.getBindingContext()
        val targetContext: BindingContext = originalTarget.getBindingContext()
        val substitution = HashMap<UnifierParameter, KtElement>()
        val declarationPatternsToTargets = MultiMap<DeclarationDescriptor, DeclarationDescriptor>()
        val weakMatches = HashMap<KtElement, KtElement>()
        var checkEquivalence: Boolean = false
        var targetSubstringInfo: K1ExtractableSubstringInfo? = null

        private fun KotlinPsiRange.getBindingContext(): BindingContext {
            val element = elements.firstOrNull() as? KtElement
            if ((element?.containingFile as? KtFile)?.doNotAnalyze != null) return BindingContext.EMPTY
            return element?.analyze() ?: BindingContext.EMPTY
        }

        private fun matchDescriptors(first: DeclarationDescriptor?, second: DeclarationDescriptor?): Boolean {
            if (DescriptorEquivalenceForOverrides.areEquivalent(first, second, allowCopiesFromTheSameDeclaration = true)) return true
            if (second in declarationPatternsToTargets[first] || first in declarationPatternsToTargets[second]) return true
            if (first == null || second == null) return false

            val firstPsi = DescriptorToSourceUtils.descriptorToDeclaration(first) as? KtDeclaration
            val secondPsi = DescriptorToSourceUtils.descriptorToDeclaration(second) as? KtDeclaration
            if (firstPsi == null || secondPsi == null) return false
            if (firstPsi == secondPsi) return true

            if ((firstPsi in originalTarget && secondPsi in originalPattern) || (secondPsi in originalTarget && firstPsi in originalPattern)) {
                return matchDeclarations(firstPsi, secondPsi, first, second) == true
            }

            return false
        }

        private fun matchReceivers(first: Receiver?, second: Receiver?): Boolean {
            return when {
                first is ExpressionReceiver && second is ExpressionReceiver ->
                    doUnify(first.expression, second.expression)
                first is ImplicitReceiver && second is ImplicitReceiver ->
                    matchDescriptors(first.declarationDescriptor, second.declarationDescriptor)
                else ->
                    first == second
            }
        }

        private fun matchCalls(first: Call, second: Call): Boolean {
            return matchReceivers(first.explicitReceiver, second.explicitReceiver)
                    && matchReceivers(first.dispatchReceiver, second.dispatchReceiver)
        }

        private fun matchArguments(first: ValueArgument, second: ValueArgument): Boolean {
            return when {
                first.isExternal() != second.isExternal() -> false
                (first.getSpreadElement() == null) != (second.getSpreadElement() == null) -> false
                else -> doUnify(first.getArgumentExpression(), second.getArgumentExpression())
            }
        }

        private fun matchResolvedCalls(first: ResolvedCall<*>, second: ResolvedCall<*>): Boolean? {
            fun checkSpecialOperations(): Boolean {
                val op1 = (first.call.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameElementType()
                val op2 = (second.call.calleeExpression as? KtSimpleNameExpression)?.getReferencedNameElementType()

                return when {
                    op1 == op2 -> true
                    op1 == KtTokens.NOT_IN || op2 == KtTokens.NOT_IN -> false
                    op1 == KtTokens.EXCLEQ || op2 == KtTokens.EXCLEQ -> false
                    op1 in OperatorConventions.COMPARISON_OPERATIONS || op2 in OperatorConventions.COMPARISON_OPERATIONS -> false
                    else -> true
                }
            }

            fun checkArguments(): Boolean? {
                val firstArguments = first.resultingDescriptor?.valueParameters?.map { first.valueArguments[it] } ?: emptyList()
                val secondArguments = second.resultingDescriptor?.valueParameters?.map { second.valueArguments[it] } ?: emptyList()
                if (firstArguments.size != secondArguments.size) return false
                if (first.call.valueArguments.size != firstArguments.size || second.call.valueArguments.size != secondArguments.size) return null

                val mappedArguments = firstArguments.asSequence().zip(secondArguments.asSequence())
                return mappedArguments.fold(true) { status, (firstArgument, secondArgument) ->
                    status && when {
                        firstArgument == secondArgument -> true
                        firstArgument == null || secondArgument == null -> false
                        else -> {
                            val mappedInnerArguments = firstArgument.arguments.asSequence().zip(secondArgument.arguments.asSequence())
                            mappedInnerArguments.fold(true) { statusForArgument, pair ->
                                statusForArgument && matchArguments(pair.first, pair.second)
                            }
                        }
                    }
                }
            }

            fun checkImplicitReceiver(implicitCall: ResolvedCall<*>, explicitCall: ResolvedCall<*>): Boolean {
                val (implicitReceiver, explicitReceiver) =
                    when (explicitCall.explicitReceiverKind) {
                        ExplicitReceiverKind.EXTENSION_RECEIVER ->
                            (implicitCall.extensionReceiver as? ImplicitReceiver) to (explicitCall.extensionReceiver as? ExpressionReceiver)

                        ExplicitReceiverKind.DISPATCH_RECEIVER ->
                            (implicitCall.dispatchReceiver as? ImplicitReceiver) to (explicitCall.dispatchReceiver as? ExpressionReceiver)

                        else ->
                            null to null
                    }

                val thisExpression = explicitReceiver?.expression?.unwrap() as? KtThisExpression
                if (implicitReceiver == null || thisExpression == null) return false

                return matchDescriptors(
                    implicitReceiver.declarationDescriptor,
                    thisExpression.getAdjustedResolvedCall()?.candidateDescriptor?.containingDeclaration
                )
            }

            fun checkReceivers(): Boolean {
                return when {
                    first.explicitReceiverKind == second.explicitReceiverKind -> {
                        if (!matchReceivers(first.extensionReceiver, second.extensionReceiver)) {
                            false
                        } else {
                            first.explicitReceiverKind == ExplicitReceiverKind.BOTH_RECEIVERS
                                    || matchReceivers(first.dispatchReceiver, second.dispatchReceiver)
                        }
                    }
                    first.explicitReceiverKind == ExplicitReceiverKind.NO_EXPLICIT_RECEIVER -> checkImplicitReceiver(first, second)
                    second.explicitReceiverKind == ExplicitReceiverKind.NO_EXPLICIT_RECEIVER -> checkImplicitReceiver(second, first)
                    else -> false
                }
            }

            fun checkTypeArguments(): Boolean? {
                val firstTypeArguments = first.typeArguments.toList()
                val secondTypeArguments = second.typeArguments.toList()
                if (firstTypeArguments.size != secondTypeArguments.size) return false

                for ((firstTypeArgument, secondTypeArgument) in (firstTypeArguments.zip(secondTypeArguments))) {
                    if (!matchDescriptors(firstTypeArgument.first, secondTypeArgument.first)) return false

                    val status = matchTypes(firstTypeArgument.second, secondTypeArgument.second)
                    if (status != true) return status
                }

                return true
            }

            return when {
                !checkSpecialOperations() -> false
                !matchDescriptors(first.candidateDescriptor, second.candidateDescriptor) -> false
                !checkReceivers() -> false
                first.call.isSafeCall() != second.call.isSafeCall() -> false
                else -> {
                    val status = checkTypeArguments()
                    if (status != true) status else checkArguments()
                }
            }
        }

        private val KtElement.bindingContext: BindingContext
            get() = if (this in originalPattern) patternContext else targetContext

        private fun KtElement.getAdjustedResolvedCall(): ResolvedCall<*>? {
            val rc = if (this is KtArrayAccessExpression) {
                bindingContext[BindingContext.INDEXED_LVALUE_GET, this]
            } else {
                getResolvedCall(bindingContext)?.let {
                    when {
                        it !is VariableAsFunctionResolvedCall -> it
                        this is KtSimpleNameExpression -> it.variableCall
                        else -> it.functionCall
                    }
                }
            }

            return when {
                rc == null || ErrorUtils.isError(rc.candidateDescriptor) -> null
                else -> rc
            }
        }

        private fun matchCalls(first: KtElement, second: KtElement): Boolean? {
            if (first.shouldIgnoreResolvedCall() || second.shouldIgnoreResolvedCall()) return null

            val firstResolvedCall = first.getAdjustedResolvedCall()
            val secondResolvedCall = second.getAdjustedResolvedCall()

            return when {
                firstResolvedCall != null && secondResolvedCall != null ->
                    matchResolvedCalls(firstResolvedCall, secondResolvedCall)

                firstResolvedCall == null && secondResolvedCall == null -> {
                    val firstCall = first.getCall(first.bindingContext)
                    val secondCall = second.getCall(second.bindingContext)

                    when {
                        firstCall != null && secondCall != null ->
                            if (matchCalls(firstCall, secondCall)) null else false

                        else ->
                            if (firstCall == null && secondCall == null) null else false
                    }
                }

                else -> false
            }
        }

        private fun matchTypes(
            firstType: KotlinType?,
            secondType: KotlinType?,
            firstTypeReference: KtTypeReference? = null,
            secondTypeReference: KtTypeReference? = null
        ): Boolean? {
            if (firstType != null && secondType != null) {
                val firstUnwrappedType = firstType.unwrap()
                val secondUnwrappedType = secondType.unwrap()
                if (firstUnwrappedType !== firstType || secondUnwrappedType !== secondType) return matchTypes(
                    firstUnwrappedType,
                    secondUnwrappedType,
                    firstTypeReference,
                    secondTypeReference
                )

                if (firstType.isError || secondType.isError) return null
                if (firstType is AbbreviatedType != secondType is AbbreviatedType) return false
                if (firstType.isExtensionFunctionType != secondType.isExtensionFunctionType) return false
                if (TypeUtils.equalTypes(firstType, secondType)) return true

                if (firstType.isMarkedNullable != secondType.isMarkedNullable) return false
                if (!matchDescriptors(
                        firstType.constructor.declarationDescriptor,
                        secondType.constructor.declarationDescriptor
                    )
                ) return false

                val firstTypeArguments = firstType.arguments
                val secondTypeArguments = secondType.arguments
                if (firstTypeArguments.size != secondTypeArguments.size) return false

                for ((index, firstTypeArgument) in firstTypeArguments.withIndex()) {
                    val secondTypeArgument = secondTypeArguments[index]
                    if (!matchTypeArguments(index, firstTypeArgument, secondTypeArgument, firstTypeReference, secondTypeReference)) {
                        return false
                    }
                }

                return true
            }

            return if (firstType == null && secondType == null) null else false
        }

        private fun matchTypeArguments(
            argIndex: Int,
            firstArgument: TypeProjection,
            secondArgument: TypeProjection,
            firstTypeReference: KtTypeReference?,
            secondTypeReference: KtTypeReference?
        ): Boolean {
            val firstArgumentReference = firstTypeReference?.typeElement?.typeArgumentsAsTypes?.getOrNull(argIndex)
            val secondArgumentReference = secondTypeReference?.typeElement?.typeArgumentsAsTypes?.getOrNull(argIndex)

            if (firstArgument.projectionKind != secondArgument.projectionKind) return false
            val firstArgumentType = firstArgument.type
            val secondArgumentType = secondArgument.type

            // Substitution attempt using either arg1, or arg2 as a pattern type. Falls back to exact matching if substitution is not possible
            val status = if (!checkEquivalence && firstTypeReference != null && secondTypeReference != null) {
                val firstTypeDeclaration = firstArgumentType.constructor.declarationDescriptor?.source?.getPsi()
                val secondTypeDeclaration = secondArgumentType.constructor.declarationDescriptor?.source?.getPsi()

                descriptorToParameter[firstTypeDeclaration]?.let { substitute(it, secondArgumentReference) }
                    ?: descriptorToParameter[secondTypeDeclaration]?.let { substitute(it, firstArgumentReference) }
                    ?: matchTypes(firstArgumentType, secondArgumentType, firstArgumentReference, secondArgumentReference)
            } else matchTypes(firstArgumentType, secondArgumentType, firstArgumentReference, secondArgumentReference)

            return status == true
        }

        private fun matchTypes(firstTypes: Collection<KotlinType>, secondTypes: Collection<KotlinType>): Boolean {
            fun sortTypes(types: Collection<KotlinType>) = types.sortedBy { DescriptorRenderer.FQ_NAMES_IN_TYPES.renderType(it) }

            if (firstTypes.size != secondTypes.size) return false
            return (sortTypes(firstTypes).zip(sortTypes(secondTypes)))
                .all { (first, second) -> matchTypes(first, second) == true }
        }

        private fun KtElement.shouldIgnoreResolvedCall(): Boolean {
            return when (this) {
                is KtConstantExpression -> true
                is KtOperationReferenceExpression -> getReferencedNameElementType() == KtTokens.EXCLEXCL
                is KtIfExpression -> true
                is KtWhenExpression -> true
                is KtUnaryExpression -> when (operationReference.getReferencedNameElementType()) {
                    KtTokens.EXCLEXCL, KtTokens.PLUSPLUS, KtTokens.MINUSMINUS -> true
                    else -> false
                }

                is KtBinaryExpression -> operationReference.getReferencedNameElementType() == KtTokens.ELVIS
                is KtThisExpression -> true
                is KtSimpleNameExpression -> getStrictParentOfType<KtTypeElement>() != null
                else -> false
            }
        }

        private fun KtBinaryExpression.matchComplexAssignmentWithSimple(expression: KtBinaryExpression): Boolean {
            return when (doUnify(left, expression.left)) {
                false -> false
                else -> expression.right?.let { matchCalls(this, it) } ?: false
            }
        }

        private fun KtBinaryExpression.matchAssignment(element: KtElement): Boolean? {
            val operationType = operationReference.getReferencedNameElementType() as KtToken
            if (operationType == KtTokens.EQ) {
                if (element.shouldIgnoreResolvedCall()) return false

                if (KtPsiUtil.isAssignment(element) && !KtPsiUtil.isOrdinaryAssignment(element)) {
                    return (element as KtBinaryExpression).matchComplexAssignmentWithSimple(this)
                }

                val lhs = left?.unwrap()
                if (lhs !is KtArrayAccessExpression) return null

                val setResolvedCall = bindingContext[BindingContext.INDEXED_LVALUE_SET, lhs]
                val resolvedCallToMatch = element.getAdjustedResolvedCall()

                return when {
                    setResolvedCall == null || resolvedCallToMatch == null -> null
                    else -> matchResolvedCalls(setResolvedCall, resolvedCallToMatch)
                }
            }

            val assignResolvedCall = getAdjustedResolvedCall() ?: return false

            val operationName = OperatorConventions.getNameForOperationSymbol(operationType)
            if (assignResolvedCall.resultingDescriptor?.name == operationName) return matchCalls(this, element)

            return if (KtPsiUtil.isAssignment(element)) null else false
        }

        private fun matchLabelTargets(first: KtLabelReferenceExpression, second: KtLabelReferenceExpression): Boolean {
            val firstTarget = first.bindingContext[BindingContext.LABEL_TARGET, first]
            val secondTarget = second.bindingContext[BindingContext.LABEL_TARGET, second]
            return firstTarget == secondTarget
        }

        private fun PsiElement.isIncrement(): Boolean {
            val parent = this.parent
            return parent is KtUnaryExpression
                    && this == parent.operationReference
                    && ((parent.operationToken as KtToken) in OperatorConventions.INCREMENT_OPERATIONS)
        }

        private fun KtCallableReferenceExpression.hasExpressionReceiver(): Boolean {
            return bindingContext[BindingContext.DOUBLE_COLON_LHS, receiverExpression] is DoubleColonLHS.Expression
        }

        private fun matchCallableReferences(first: KtCallableReferenceExpression, second: KtCallableReferenceExpression): Boolean? {
            if (first.hasExpressionReceiver() || second.hasExpressionReceiver()) return null
            val firstDescriptor = first.bindingContext[BindingContext.REFERENCE_TARGET, first.callableReference]
            val secondDescriptor = second.bindingContext[BindingContext.REFERENCE_TARGET, second.callableReference]
            return matchDescriptors(firstDescriptor, secondDescriptor)
        }

        private fun matchThisExpressions(e1: KtThisExpression, e2: KtThisExpression): Boolean {
            val d1 = e1.bindingContext[BindingContext.REFERENCE_TARGET, e1.instanceReference]
            val d2 = e2.bindingContext[BindingContext.REFERENCE_TARGET, e2.instanceReference]
            return matchDescriptors(d1, d2)
        }

        private fun matchDestructuringDeclarations(e1: KtDestructuringDeclaration, e2: KtDestructuringDeclaration): Boolean {
            val entries1 = e1.entries
            val entries2 = e2.entries
            if (entries1.size != entries2.size) return false

            return entries1.zip(entries2).all { p ->
                val (entry1, entry2) = p
                val rc1 = entry1.bindingContext[BindingContext.COMPONENT_RESOLVED_CALL, entry1]
                val rc2 = entry2.bindingContext[BindingContext.COMPONENT_RESOLVED_CALL, entry2]
                when {
                    rc1 == null && rc2 == null -> true
                    rc1 != null && rc2 != null -> matchResolvedCalls(rc1, rc2) == true
                    else -> false
                }
            }
        }

        fun matchReceiverParameters(firstReceiver: ReceiverParameterDescriptor?, secondReceiver: ReceiverParameterDescriptor?): Boolean {
            val matchedReceivers = when {
                firstReceiver == null && secondReceiver == null -> true
                matchDescriptors(firstReceiver, secondReceiver) -> true
                firstReceiver != null && secondReceiver != null -> matchTypes(firstReceiver.type, secondReceiver.type) == true
                else -> false
            }

            if (matchedReceivers && firstReceiver != null) {
                declarationPatternsToTargets.putValue(firstReceiver, secondReceiver)
            }

            return matchedReceivers
        }

        private fun matchCallables(
            first: KtDeclaration,
            second: KtDeclaration,
            firstDescriptor: CallableDescriptor,
            secondDescriptor: CallableDescriptor
        ): Boolean {
            if (firstDescriptor is VariableDescriptor && firstDescriptor.isVar != (secondDescriptor as VariableDescriptor).isVar) {
                return false
            }

            if (!matchNames(first, second, firstDescriptor, secondDescriptor)) {
                return false
            }

            fun needToCompareReturnTypes(): Boolean {
                if (first !is KtCallableDeclaration) return true
                return first.typeReference != null || (second as KtCallableDeclaration).typeReference != null
            }

            if (needToCompareReturnTypes()) {
                val type1 = firstDescriptor.returnType
                val type2 = secondDescriptor.returnType

                if (type1 != type2 && (type1 == null || type2 == null || type1.isError || type2.isError || matchTypes(
                        type1,
                        type2
                    ) != true)
                ) {
                    return false
                }
            }

            if (!matchReceiverParameters(firstDescriptor.extensionReceiverParameter, secondDescriptor.extensionReceiverParameter)) {
                return false
            }

            if (!matchReceiverParameters(firstDescriptor.dispatchReceiverParameter, secondDescriptor.dispatchReceiverParameter)) {
                return false
            }

            val params1 = firstDescriptor.valueParameters
            val params2 = secondDescriptor.valueParameters
            val zippedParams = params1.zip(params2)
            val parametersMatch =
                (params1.size == params2.size) && zippedParams.all { matchTypes(it.first.type, it.second.type) == true }
            if (!parametersMatch) return false

            zippedParams.forEach { declarationPatternsToTargets.putValue(it.first, it.second) }

            return doUnify(
                (first as? KtTypeParameterListOwner)?.typeParameters?.toRange() ?: Empty,
                (second as? KtTypeParameterListOwner)?.typeParameters?.toRange() ?: Empty
            ) && when (first) {
                is KtDeclarationWithBody -> doUnify(first.bodyExpression, (second as KtDeclarationWithBody).bodyExpression)
                is KtDeclarationWithInitializer -> doUnify(first.initializer, (second as KtDeclarationWithInitializer).initializer)
                is KtParameter -> doUnify(first.defaultValue, (second as KtParameter).defaultValue)
                else -> false
            }
        }

        private fun KtDeclaration.isNameRelevant(): Boolean {
            if (this is KtParameter && hasValOrVar()) return true

            val parent = parent
            return parent is KtClassBody || parent is KtFile
        }

        private fun matchNames(
            first: KtDeclaration,
            second: KtDeclaration,
            firstDescriptor: DeclarationDescriptor,
            secondDescriptor: DeclarationDescriptor
        ): Boolean {
            return (!first.isNameRelevant() && !second.isNameRelevant())
                    || firstDescriptor.name == secondDescriptor.name
        }

        private fun matchClasses(
            first: KtClassOrObject,
            second: KtClassOrObject,
            firstDescriptor: ClassDescriptor,
            secondDescriptor: ClassDescriptor
        ): Boolean {
            class OrderInfo<out T>(
                val orderSensitive: List<T>,
                val orderInsensitive: List<T>
            )

            fun getMemberOrderInfo(cls: KtClassOrObject): OrderInfo<KtDeclaration> {
                val (orderInsensitive, orderSensitive) = (cls.body?.declarations ?: Collections.emptyList()).partition {
                    it is KtClassOrObject || it is KtFunction
                }

                return OrderInfo(orderSensitive, orderInsensitive)
            }

            fun getDelegationOrderInfo(cls: KtClassOrObject): OrderInfo<KtSuperTypeListEntry> {
                val (orderInsensitive, orderSensitive) = cls.superTypeListEntries.partition { it is KtSuperTypeEntry }
                return OrderInfo(orderSensitive, orderInsensitive)
            }

            fun resolveAndSortDeclarationsByDescriptor(declarations: List<KtDeclaration>): List<Pair<KtDeclaration, DeclarationDescriptor?>> {
                return declarations.map { it to it.bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
                    .sortedBy { it.second?.let { descriptor -> IdeDescriptorRenderers.SOURCE_CODE.render(descriptor) } ?: "" }
            }

            fun sortDeclarationsByElementType(declarations: List<KtDeclaration>): List<KtDeclaration> {
                return declarations.sortedBy { it.node?.elementType?.index ?: -1 }
            }

            if (firstDescriptor.kind != secondDescriptor.kind) return false
            if (!matchNames(first, second, firstDescriptor, secondDescriptor)) return false

            declarationPatternsToTargets.putValue(firstDescriptor.thisAsReceiverParameter, secondDescriptor.thisAsReceiverParameter)

            val firstConstructor = firstDescriptor.unsubstitutedPrimaryConstructor
            val secondConstructor = secondDescriptor.unsubstitutedPrimaryConstructor
            if (firstConstructor != null && secondConstructor != null) {
                declarationPatternsToTargets.putValue(firstConstructor, secondConstructor)
            }

            val firstOrderInfo = getDelegationOrderInfo(first)
            val secondOrderInfo = getDelegationOrderInfo(second)

            if (firstOrderInfo.orderInsensitive.size != secondOrderInfo.orderInsensitive.size) return false

            outer@ for (firstSpecifier in firstOrderInfo.orderInsensitive) {
                for (secondSpecifier in secondOrderInfo.orderInsensitive) {
                    if (doUnify(firstSpecifier, secondSpecifier)) continue@outer
                }
                return false
            }

            val firstParameters = (first as? KtClass)?.getPrimaryConstructorParameterList()
            val secondParameters = (second as? KtClass)?.getPrimaryConstructorParameterList()
            val status = doUnify(firstParameters, secondParameters)
                    && doUnify((first as? KtClass)?.typeParameterList, (second as? KtClass)?.typeParameterList)
                    && doUnify(firstOrderInfo.orderSensitive.toRange(), secondOrderInfo.orderSensitive.toRange())

            if (!status) return false

            val firstMemberInfo = getMemberOrderInfo(first)
            val secondMemberInfo = getMemberOrderInfo(second)

            val firstSortedMembers = resolveAndSortDeclarationsByDescriptor(firstMemberInfo.orderInsensitive)
            val secondSortedMembers = resolveAndSortDeclarationsByDescriptor(secondMemberInfo.orderInsensitive)
            if ((firstSortedMembers.size != secondSortedMembers.size)) return false

            for ((index, firstSortedMember) in firstSortedMembers.withIndex()) {
                val (firstMember, firstMemberDescriptor) = firstSortedMember
                val (secondMember, secondMemberDescriptor) = secondSortedMembers[index]
                val memberResult = matchDeclarations(firstMember, secondMember, firstMemberDescriptor, secondMemberDescriptor)
                    ?: doUnify(firstMember, secondMember)

                if (!memberResult) {
                    return false
                }
            }

            return doUnify(
                sortDeclarationsByElementType(firstMemberInfo.orderSensitive).toRange(),
                sortDeclarationsByElementType(secondMemberInfo.orderSensitive).toRange()
            )
        }

        private fun matchTypeParameters(first: TypeParameterDescriptor, second: TypeParameterDescriptor): Boolean {
            if (first.variance != second.variance) return false
            if (!matchTypes(first.upperBounds, second.upperBounds)) return false
            return true
        }

        private fun KtDeclaration.matchDeclarations(element: PsiElement): Boolean? {
            if (element !is KtDeclaration) return false

            val firstDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, this]
            val secondDescriptor = element.bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
            return matchDeclarations(this, element, firstDescriptor, secondDescriptor)
        }

        private fun matchDeclarations(
            decl1: KtDeclaration,
            decl2: KtDeclaration,
            desc1: DeclarationDescriptor?,
            desc2: DeclarationDescriptor?
        ): Boolean? {
            if (decl1::class.java != decl2::class.java) return false

            if (desc1 == null || desc2 == null) {
                return if (decl1 is KtParameter
                    && decl2 is KtParameter
                    && decl1.getStrictParentOfType<KtTypeElement>() != null
                    && decl2.getStrictParentOfType<KtTypeElement>() != null
                )
                    null
                else
                    false
            }
            if (ErrorUtils.isError(desc1) || ErrorUtils.isError(desc2)) return false
            if (desc1::class.java != desc2::class.java) return false

            declarationPatternsToTargets.putValue(desc1, desc2)
            val status = when (decl1) {
                is KtDeclarationWithBody, is KtDeclarationWithInitializer, is KtParameter ->
                    matchCallables(decl1, decl2, desc1 as CallableDescriptor, desc2 as CallableDescriptor)

                is KtClassOrObject ->
                    matchClasses(decl1, decl2 as KtClassOrObject, desc1 as ClassDescriptor, desc2 as ClassDescriptor)

                is KtTypeParameter ->
                    matchTypeParameters(desc1 as TypeParameterDescriptor, desc2 as TypeParameterDescriptor)

                else ->
                    null
            }
            if (status == false) {
                declarationPatternsToTargets.remove(desc1, desc2)
            }

            return status
        }

        private fun matchResolvedInfo(first: PsiElement, second: PsiElement): Boolean? {
            fun KtTypeReference.getType(): KotlinType? {
                return (bindingContext[BindingContext.ABBREVIATED_TYPE, this] ?: bindingContext[BindingContext.TYPE, this])
                    ?.takeUnless { it.isError }
            }

            return when {
                first !is KtElement || second !is KtElement ->
                    null

                first is KtDestructuringDeclaration && second is KtDestructuringDeclaration ->
                    if (matchDestructuringDeclarations(first, second)) null else false

                first is KtAnonymousInitializer && second is KtAnonymousInitializer ->
                    null

                first is KtDeclaration ->
                    first.matchDeclarations(second)

                second is KtDeclaration ->
                    second.matchDeclarations(first)

                first is KtTypeElement && second is KtTypeElement && first.parent is KtTypeReference && second.parent is KtTypeReference ->
                    matchResolvedInfo(first.parent, second.parent)

                first is KtTypeReference && second is KtTypeReference ->
                    matchTypes(first.getType(), second.getType(), first, second)

                KtPsiUtil.isAssignment(first) ->
                    (first as KtBinaryExpression).matchAssignment(second)

                KtPsiUtil.isAssignment(second) ->
                    (second as KtBinaryExpression).matchAssignment(first)

                first is KtLabelReferenceExpression && second is KtLabelReferenceExpression ->
                    matchLabelTargets(first, second)

                first.isIncrement() != second.isIncrement() ->
                    false

                first is KtCallableReferenceExpression && second is KtCallableReferenceExpression ->
                    matchCallableReferences(first, second)

                first is KtThisExpression && second is KtThisExpression -> matchThisExpressions(first, second)

                else ->
                    matchCalls(first, second)
            }
        }

        private fun PsiElement.checkType(parameter: UnifierParameter): Boolean {
            val expectedType = parameter.expectedType ?: return true
            val targetElementType = (this as? KtExpression)?.let { it.bindingContext.getType(it) }
            return targetElementType != null && KotlinTypeChecker.DEFAULT.isSubtypeOf(targetElementType, expectedType)
        }

        private fun doUnifyStringTemplateFragments(target: KtStringTemplateExpression, pattern: K1ExtractableSubstringInfo): Boolean {
            val prefixLength = pattern.prefix.length
            val suffixLength = pattern.suffix.length
            val targetEntries = target.entries
            val patternEntries = pattern.entries.toList()
            for ((index, targetEntry) in targetEntries.withIndex()) {
                if (index + patternEntries.size > targetEntries.size) return false

                val targetEntryText = targetEntry.text

                if (pattern.startEntry == pattern.endEntry && (prefixLength > 0 || suffixLength > 0)) {
                    if (targetEntry !is KtLiteralStringTemplateEntry) continue

                    val patternText = with(pattern.startEntry.text) { substring(prefixLength, length - suffixLength) }
                    val i = targetEntryText.indexOf(patternText)
                    if (i < 0) continue
                    val targetPrefix = targetEntryText.substring(0, i)
                    val targetSuffix = targetEntryText.substring(i + patternText.length)
                    targetSubstringInfo = K1ExtractableSubstringInfo(targetEntry, targetEntry, targetPrefix, targetSuffix, pattern.type)
                    return true
                }

                val matchStartByText = pattern.startEntry is KtLiteralStringTemplateEntry
                val matchEndByText = pattern.endEntry is KtLiteralStringTemplateEntry

                val targetPrefix = if (matchStartByText) {
                    if (targetEntry !is KtLiteralStringTemplateEntry) continue

                    val patternText = pattern.startEntry.text.substring(prefixLength)
                    if (!targetEntryText.endsWith(patternText)) continue
                    targetEntryText.substring(0, targetEntryText.length - patternText.length)
                } else ""

                val lastTargetEntry = targetEntries[index + patternEntries.lastIndex]

                val targetSuffix = if (matchEndByText) {
                    if (lastTargetEntry !is KtLiteralStringTemplateEntry) continue

                    val patternText = with(pattern.endEntry.text) { substring(0, length - suffixLength) }
                    val lastTargetEntryText = lastTargetEntry.text
                    if (!lastTargetEntryText.startsWith(patternText)) continue
                    lastTargetEntryText.substring(patternText.length)
                } else ""

                val fromIndex = if (matchStartByText) 1 else 0
                val toIndex = if (matchEndByText) patternEntries.lastIndex - 1 else patternEntries.lastIndex
                val status = (fromIndex..toIndex).fold(true) { status, patternEntryIndex ->
                    val targetEntryToUnify = targetEntries[index + patternEntryIndex]
                    val patternEntryToUnify = patternEntries[patternEntryIndex]
                    status && doUnify(targetEntryToUnify, patternEntryToUnify)
                }
                if (!status) continue
                targetSubstringInfo = K1ExtractableSubstringInfo(targetEntry, lastTargetEntry, targetPrefix, targetSuffix, pattern.type)
                return true
            }

            return false
        }

        fun doUnify(target: KotlinPsiRange, pattern: KotlinPsiRange): Boolean {
            val singleExpression = pattern.elements.singleOrNull() as? KtExpression
            (singleExpression?.extractableSubstringInfo as? K1ExtractableSubstringInfo)?.let {
                val targetTemplate = target.elements.singleOrNull() as? KtStringTemplateExpression ?: return false
                return doUnifyStringTemplateFragments(targetTemplate, it)
            }

            val targetElements = target.elements
            val patternElements = pattern.elements
            if (targetElements.size != patternElements.size) return false

            return (targetElements.asSequence().zip(patternElements.asSequence())).fold(true) { status, (first, second) ->
                status && doUnify(first, second)
            }
        }

        private fun ASTNode.getChildrenRange(): KotlinPsiRange = getChildren(null).mapNotNull { it.psi }.toRange()

        private fun PsiElement.unwrapWeakly(): KtElement? {
            return when {
                this is KtReturnExpression -> returnedExpression
                this is KtProperty -> initializer
                KtPsiUtil.isOrdinaryAssignment(this) -> (this as KtBinaryExpression).right
                this is KtExpression && this !is KtDeclaration -> this
                else -> null
            }
        }

        private fun doUnifyWeakly(targetElement: KtElement, patternElement: KtElement): Boolean {
            if (!allowWeakMatches) return false

            val targetElementUnwrapped = targetElement.unwrapWeakly()
            val patternElementUnwrapped = patternElement.unwrapWeakly()
            if (targetElementUnwrapped == null || patternElementUnwrapped == null) return false
            if (targetElementUnwrapped == targetElement && patternElementUnwrapped == patternElement) return false

            val status = doUnify(targetElementUnwrapped, patternElementUnwrapped)
            if (status) {
                weakMatches[patternElement] = targetElement
            }

            return status
        }

        private fun substitute(parameter: UnifierParameter, targetElement: PsiElement?): Boolean {
            return when (val existingArgument = substitution[parameter]) {
                null -> {
                    substitution[parameter] = targetElement as KtElement
                    true
                }

                else -> {
                    checkEquivalence = true
                    val status = doUnify(existingArgument, targetElement)
                    checkEquivalence = false

                    status
                }
            }
        }

        fun doUnify(
            targetElement: PsiElement?,
            patternElement: PsiElement?
        ): Boolean {
            val targetElementUnwrapped = targetElement?.unwrap()
            val patternElementUnwrapped = patternElement?.unwrap()

            if (targetElementUnwrapped == patternElementUnwrapped) return true
            if (targetElementUnwrapped == null || patternElementUnwrapped == null) return false

            if (!checkEquivalence && targetElementUnwrapped !is KtBlockExpression) {
                val referencedPatternDescriptor = when (patternElementUnwrapped) {
                    is KtReferenceExpression -> {
                        if (targetElementUnwrapped !is KtExpression) return false
                        patternElementUnwrapped.bindingContext[BindingContext.REFERENCE_TARGET, patternElementUnwrapped]
                    }
                    is KtUserType -> {
                        if (targetElementUnwrapped !is KtUserType) return false
                        patternElementUnwrapped.bindingContext[BindingContext.REFERENCE_TARGET, patternElementUnwrapped.referenceExpression]
                    }
                    else -> null
                }
                val referencedPatternDeclaration = (referencedPatternDescriptor as? DeclarationDescriptorWithSource)?.source?.getPsi()
                val parameter = descriptorToParameter[referencedPatternDeclaration]
                if (referencedPatternDeclaration != null && parameter != null) {
                    if (targetElementUnwrapped is KtExpression) {
                        if (!targetElementUnwrapped.checkType(parameter)) return false
                    }

                    return substitute(parameter, targetElementUnwrapped)
                }
            }

            val targetNode = targetElementUnwrapped.node
            val patternNode = patternElementUnwrapped.node
            if (targetNode == null || patternNode == null) return false

            val resolvedStatus = matchResolvedInfo(targetElementUnwrapped, patternElementUnwrapped)
            if (resolvedStatus == true) return resolvedStatus

            if (targetElementUnwrapped is KtElement && patternElementUnwrapped is KtElement) {
                val weakStatus = doUnifyWeakly(targetElementUnwrapped, patternElementUnwrapped)
                if (weakStatus) return true
            }

            if (targetNode.elementType != patternNode.elementType) return false

            if (resolvedStatus != null) return resolvedStatus

            val targetChildren = targetNode.getChildrenRange()
            val patternChildren = patternNode.getChildrenRange()

            if (patternChildren.isEmpty && targetChildren.isEmpty) {
                return targetElementUnwrapped.unquotedText() == patternElementUnwrapped.unquotedText()
            }

            return doUnify(targetChildren, patternChildren)
        }
    }

    private val descriptorToParameter = parameters.associateBy { (it.descriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() }

    private fun PsiElement.unwrap(): PsiElement? = when (this) {
        is KtExpression -> KtPsiUtil.deparenthesize(this)
        is KtStringTemplateEntryWithExpression -> KtPsiUtil.deparenthesize(expression)
        else -> this
    }

    private fun PsiElement.unquotedText(): String {
        val text = text ?: ""
        return if (this is LeafPsiElement) KtPsiUtil.unquoteIdentifier(text) else text
    }

    fun unify(target: KotlinPsiRange, pattern: KotlinPsiRange): KotlinPsiUnificationResult {
        return with(Context(target, pattern)) {
            val status = doUnify(target, pattern)
            when {
                substitution.size != descriptorToParameter.size -> Failure
                status -> {
                    val targetRange = targetSubstringInfo?.createExpression()?.toRange() ?: target
                    if (weakMatches.isEmpty()) {
                        StrictSuccess(targetRange, substitution)
                    } else {
                        WeakSuccess(targetRange, substitution, weakMatches)
                    }
                }
                else -> Failure
            }
        }
    }

    fun unify(targetElement: PsiElement?, patternElement: PsiElement?): KotlinPsiUnificationResult =
        unify(targetElement.toRange(), patternElement.toRange())
}

fun PsiElement?.matches(e: PsiElement?): Boolean = KotlinPsiUnifier.DEFAULT.unify(this, e).isMatched
fun KotlinPsiRange.matches(r: KotlinPsiRange): Boolean = KotlinPsiUnifier.DEFAULT.unify(this, r).isMatched

fun KotlinPsiRange.match(scope: PsiElement, unifier: KotlinPsiUnifier): List<Success<UnifierParameter>> {
    return match(scope) { target, pattern ->
        @Suppress("UNCHECKED_CAST")
        unifier.unify(target, pattern) as? Success<UnifierParameter>
    }
}