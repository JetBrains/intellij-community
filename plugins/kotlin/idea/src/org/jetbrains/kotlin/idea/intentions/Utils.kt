// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.isKSuspendFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.unpackFunctionLiteral
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.types.toDefaultAttributes
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.util.OperatorChecks
import org.jetbrains.kotlin.util.OperatorNameConventions

// returns assignment which replaces initializer
@K1Deprecation
fun splitPropertyDeclaration(property: KtProperty): KtBinaryExpression? {
    val parent = property.parent

    val initializer = property.initializer ?: return null

    val explicitTypeToSet = if (property.typeReference != null) null else initializer.analyze().getType(initializer)

    val psiFactory = KtPsiFactory(property.project)
    var assignment = psiFactory.createExpressionByPattern("$0 = $1", property.nameAsName!!, initializer)

    assignment = parent.addAfter(assignment, property) as KtBinaryExpression
    parent.addAfter(psiFactory.createNewLine(), property)

    property.initializer = null

    if (explicitTypeToSet != null) {
        property.setType(explicitTypeToSet)
    }

    return assignment
}

@K1Deprecation
val KtQualifiedExpression.callExpression: KtCallExpression?
    get() = selectorExpression as? KtCallExpression

@K1Deprecation
val KtQualifiedExpression.calleeName: String?
    get() = (callExpression?.calleeExpression as? KtNameReferenceExpression)?.text

@K1Deprecation
fun KtQualifiedExpression.toResolvedCall(bodyResolveMode: BodyResolveMode): ResolvedCall<out CallableDescriptor>? =
    callExpression?.resolveToCall(bodyResolveMode)

// returns false for call of super, static method or method from package
@K1Deprecation
fun KtQualifiedExpression.isReceiverExpressionWithValue(): Boolean {
    val receiver = receiverExpression
    if (receiver is KtSuperExpression) return false
    return analyze().getType(receiver) != null
}

@K1Deprecation
fun KtExpression.isBooleanExpression(): Boolean {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext.getType(this) ?: return false
    return KotlinBuiltIns.isBoolean(type)
}

@K1Deprecation
fun KtExpression.negate(reformat: Boolean = true): KtExpression {
    return negate(reformat) { it.isBooleanExpression() }
}

@K1Deprecation
fun KtExpression?.hasResultingIfWithoutElse(): Boolean = when (this) {
    is KtIfExpression -> `else` == null || then.hasResultingIfWithoutElse() || `else`.hasResultingIfWithoutElse()
    is KtWhenExpression -> entries.any { it.expression.hasResultingIfWithoutElse() }
    is KtBinaryExpression -> left.hasResultingIfWithoutElse() || right.hasResultingIfWithoutElse()
    is KtUnaryExpression -> baseExpression.hasResultingIfWithoutElse()
    is KtBlockExpression -> statements.lastOrNull().hasResultingIfWithoutElse()
    else -> false
}

internal fun KotlinType.isFlexibleRecursive(): Boolean {
    if (isFlexible()) return true
    return arguments.any { !it.isStarProjection && it.type.isFlexibleRecursive() }
}

@K1Deprecation
val KtIfExpression.branches: List<KtExpression?> get() = ifBranchesOrThis()

private fun KtExpression.ifBranchesOrThis(): List<KtExpression?> {
    if (this !is KtIfExpression) return listOf(this)
    return listOf(then) + `else`?.ifBranchesOrThis().orEmpty()
}

@K1Deprecation
fun ResolvedCall<out CallableDescriptor>.resolvedToArrayType(): Boolean =
    resultingDescriptor.returnType.let { type ->
        type != null && (KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type))
    }

@K1Deprecation
fun KtElement?.isZero() = this?.text == "0"

@K1Deprecation
fun KtElement?.isOne() = this?.text == "1"

@K1Deprecation
fun KtExpression?.receiverTypeIfSelectorIsSizeOrLength(): KotlinType? {
    val selector = (this as? KtDotQualifiedExpression)?.selectorExpression ?: this
    val predicate: (KotlinType) -> Boolean = when (selector?.text) {
        "size" -> { type ->
            KotlinBuiltIns.isArray(type) ||
                    KotlinBuiltIns.isPrimitiveArray(type) ||
                    KotlinBuiltIns.isCollectionOrNullableCollection(type) ||
                    KotlinBuiltIns.isMapOrNullableMap(type)
        }

        "length" -> KotlinBuiltIns::isCharSequenceOrNullableCharSequence
        else -> return null
    }
    val resolvedCall = selector.resolveToCall() ?: return null
    val receiverType = (resolvedCall.dispatchReceiver ?: resolvedCall.extensionReceiver)?.type ?: return null
    return receiverType.takeIf { (it.constructor.supertypes + it).any(predicate) }
}

@K1Deprecation
fun KtExpression?.isSizeOrLength() = receiverTypeIfSelectorIsSizeOrLength() != null

private val COUNT_FUNCTIONS = listOf(FqName("kotlin.collections.count"), FqName("kotlin.text.count"))

@K1Deprecation
fun KtExpression.isCountCall(predicate: (KtCallExpression) -> Boolean = { true }): Boolean {
    val callExpression = this as? KtCallExpression
        ?: (this as? KtQualifiedExpression)?.callExpression
        ?: return false
    if (!predicate(callExpression)) return false
    return callExpression.isCalling(COUNT_FUNCTIONS)
}

private val ARRAY_OF_FUNCTION_NAMES = setOf(ArrayFqNames.ARRAY_OF_FUNCTION) +
        ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values.toSet() +
        Name.identifier("emptyArray")

@K1Deprecation
fun KtCallExpression.isArrayOfFunction(): Boolean {
    val resolvedCall = resolveToCall() ?: return false
    val descriptor = resolvedCall.candidateDescriptor
    return (descriptor.containingDeclaration as? PackageFragmentDescriptor)?.fqName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
            ARRAY_OF_FUNCTION_NAMES.contains(descriptor.name)
}

internal fun KtExpression.getCallableDescriptor() = resolveToCall()?.resultingDescriptor

@K1Deprecation
fun KtDeclaration.isFinalizeMethod(descriptor: DeclarationDescriptor? = null): Boolean {
    if (containingClass() == null) return false
    val function = this as? KtNamedFunction ?: return false
    return function.name == "finalize"
            && function.valueParameters.isEmpty()
            && ((descriptor ?: function.descriptor) as? FunctionDescriptor)?.returnType?.isUnit() == true
}

@K1Deprecation
fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != "toString") return false
    val resolvedCall = toResolvedCall(BodyResolveMode.PARTIAL) ?: return false
    val callableDescriptor = resolvedCall.resultingDescriptor as? CallableMemberDescriptor ?: return false
    return callableDescriptor.getDeepestSuperDeclarations().any { it.fqNameUnsafe.asString() == "kotlin.Any.toString" }
}

@K1Deprecation
val FunctionDescriptor.isOperatorOrCompatible: Boolean
    get() {
        if (this is JavaMethodDescriptor) {
            return OperatorChecks.check(this).isSuccess
        }
        return isOperator
    }

@K1Deprecation
fun KotlinType.reflectToRegularFunctionType(): KotlinType {
    val isTypeAnnotatedWithExtensionFunctionType = annotations.findAnnotation(StandardNames.FqNames.extensionFunctionType) != null
    val parameterCount = if (isTypeAnnotatedWithExtensionFunctionType) arguments.size - 2 else arguments.size - 1
    val classDescriptor =
        if (isKSuspendFunctionType) builtIns.getSuspendFunction(parameterCount) else builtIns.getFunction(parameterCount)
    return KotlinTypeFactory.simpleNotNullType(annotations.toDefaultAttributes(), classDescriptor, arguments)
}

@K1Deprecation
val CallableDescriptor.isInvokeOperator: Boolean
    get() = this is FunctionDescriptor && this !is FunctionInvokeDescriptor && isOperator && name == OperatorNameConventions.INVOKE

@K1Deprecation
fun KtCallExpression.canBeReplacedWithInvokeCall(): Boolean {
    return resolveToCall()?.canBeReplacedWithInvokeCall() == true
}

@K1Deprecation
fun ResolvedCall<out CallableDescriptor>.canBeReplacedWithInvokeCall(): Boolean {
    val descriptor = resultingDescriptor as? SimpleFunctionDescriptor ?: return false
    return (descriptor is FunctionInvokeDescriptor || descriptor.isInvokeOperator) && !descriptor.isExtension
}

@K1Deprecation
fun CallableDescriptor.receiverType(): KotlinType? = (dispatchReceiverParameter ?: extensionReceiverParameter)?.type

@K1Deprecation
@Deprecated("Use org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringUtilKt.singleLambdaArgumentExpression")
fun KtCallExpression.singleLambdaArgumentExpression(): KtLambdaExpression? {
    return lambdaArguments.singleOrNull()?.getArgumentExpression()?.unpackFunctionLiteral() ?: getLastLambdaExpression()
}

private val rangeTypes = setOf(
    "kotlin.ranges.IntRange",
    "kotlin.ranges.CharRange",
    "kotlin.ranges.LongRange",
    "kotlin.ranges.UIntRange",
    "kotlin.ranges.ULongRange"
)

@K1Deprecation
fun ClassDescriptor.isRange(): Boolean {
    return rangeTypes.any { this.fqNameUnsafe.asString() == it }
}
