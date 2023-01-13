// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.builtins.isKSuspendFunctionType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.codeinsight.utils.ENUM_STATIC_METHODS
import org.jetbrains.kotlin.idea.core.getDeepestSuperDeclarations
import org.jetbrains.kotlin.idea.core.getLastLambdaExpression
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.ArrayFqNames
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
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

val KtQualifiedExpression.callExpression: KtCallExpression?
    get() = selectorExpression as? KtCallExpression

val KtQualifiedExpression.calleeName: String?
    get() = (callExpression?.calleeExpression as? KtNameReferenceExpression)?.text

fun KtQualifiedExpression.toResolvedCall(bodyResolveMode: BodyResolveMode): ResolvedCall<out CallableDescriptor>? =
    callExpression?.resolveToCall(bodyResolveMode)

fun KtExpression.isExitStatement(): Boolean = when (this) {
    is KtContinueExpression, is KtBreakExpression, is KtThrowExpression, is KtReturnExpression -> true
    else -> false
}

// returns false for call of super, static method or method from package
fun KtQualifiedExpression.isReceiverExpressionWithValue(): Boolean {
    val receiver = receiverExpression
    if (receiver is KtSuperExpression) return false
    return analyze().getType(receiver) != null
}

fun KtExpression.isBooleanExpression(): Boolean {
    val bindingContext = analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext.getType(this) ?: return false
    return KotlinBuiltIns.isBoolean(type)
}

fun KtExpression.negate(reformat: Boolean = true): KtExpression {
    return negate(reformat) { it.isBooleanExpression() }
}

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

val KtIfExpression.branches: List<KtExpression?> get() = ifBranchesOrThis()

private fun KtExpression.ifBranchesOrThis(): List<KtExpression?> {
    if (this !is KtIfExpression) return listOf(this)
    return listOf(then) + `else`?.ifBranchesOrThis().orEmpty()
}

fun ResolvedCall<out CallableDescriptor>.resolvedToArrayType(): Boolean =
    resultingDescriptor.returnType.let { type ->
        type != null && (KotlinBuiltIns.isArray(type) || KotlinBuiltIns.isPrimitiveArray(type))
    }

fun KtElement?.isZero() = this?.text == "0"

fun KtElement?.isOne() = this?.text == "1"

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

fun KtExpression?.isSizeOrLength() = receiverTypeIfSelectorIsSizeOrLength() != null

private val COUNT_FUNCTIONS = listOf(FqName("kotlin.collections.count"), FqName("kotlin.text.count"))

fun KtExpression.isCountCall(predicate: (KtCallExpression) -> Boolean = { true }): Boolean {
    val callExpression = this as? KtCallExpression
        ?: (this as? KtQualifiedExpression)?.callExpression
        ?: return false
    if (!predicate(callExpression)) return false
    return callExpression.isCalling(COUNT_FUNCTIONS)
}

fun KtDotQualifiedExpression.deleteFirstReceiver(): KtExpression {
    when (val receiver = receiverExpression) {
        is KtDotQualifiedExpression -> receiver.deleteFirstReceiver()
        else -> selectorExpression?.let { return this.replace(it) as KtExpression }
    }
    return this
}

private val ARRAY_OF_FUNCTION_NAMES = setOf(ArrayFqNames.ARRAY_OF_FUNCTION) +
        ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY.values.toSet() +
        Name.identifier("emptyArray")

fun KtCallExpression.isArrayOfFunction(): Boolean {
    val resolvedCall = resolveToCall() ?: return false
    val descriptor = resolvedCall.candidateDescriptor
    return (descriptor.containingDeclaration as? PackageFragmentDescriptor)?.fqName == StandardNames.BUILT_INS_PACKAGE_FQ_NAME &&
            ARRAY_OF_FUNCTION_NAMES.contains(descriptor.name)
}

fun KtBlockExpression.getParentLambdaLabelName(): String? {
    val lambdaExpression = getStrictParentOfType<KtLambdaExpression>() ?: return null
    val callExpression = lambdaExpression.getStrictParentOfType<KtCallExpression>() ?: return null
    val valueArgument = callExpression.valueArguments.find {
        it.getArgumentExpression()?.unpackFunctionLiteral(allowParentheses = false) === lambdaExpression
    } ?: return null
    val lambdaLabelName = (valueArgument.getArgumentExpression() as? KtLabeledExpression)?.getLabelName()
    return lambdaLabelName ?: callExpression.getCallNameExpression()?.text
}

internal fun KtExpression.getCallableDescriptor() = resolveToCall()?.resultingDescriptor

fun KtDeclaration.isFinalizeMethod(descriptor: DeclarationDescriptor? = null): Boolean {
    if (containingClass() == null) return false
    val function = this as? KtNamedFunction ?: return false
    return function.name == "finalize"
            && function.valueParameters.isEmpty()
            && ((descriptor ?: function.descriptor) as? FunctionDescriptor)?.returnType?.isUnit() == true
}

fun KtDotQualifiedExpression.isToString(): Boolean {
    val callExpression = selectorExpression as? KtCallExpression ?: return false
    val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    if (referenceExpression.getReferencedName() != "toString") return false
    val resolvedCall = toResolvedCall(BodyResolveMode.PARTIAL) ?: return false
    val callableDescriptor = resolvedCall.resultingDescriptor as? CallableMemberDescriptor ?: return false
    return callableDescriptor.getDeepestSuperDeclarations().any { it.fqNameUnsafe.asString() == "kotlin.Any.toString" }
}

val FunctionDescriptor.isOperatorOrCompatible: Boolean
    get() {
        if (this is JavaMethodDescriptor) {
            return OperatorChecks.check(this).isSuccess
        }
        return isOperator
    }

internal fun Sequence<PsiElement>.lastWithPersistedElementOrNull(elementShouldPersist: KtExpression): PsiElement? {
    var lastElement: PsiElement? = null
    var checked = false

    for (element in this) {
        checked = checked || (element === elementShouldPersist)
        lastElement = element
    }

    return if (checked) lastElement else null
}

fun KotlinType.reflectToRegularFunctionType(): KotlinType {
    val isTypeAnnotatedWithExtensionFunctionType = annotations.findAnnotation(StandardNames.FqNames.extensionFunctionType) != null
    val parameterCount = if (isTypeAnnotatedWithExtensionFunctionType) arguments.size - 2 else arguments.size - 1
    val classDescriptor =
        if (isKSuspendFunctionType) builtIns.getSuspendFunction(parameterCount) else builtIns.getFunction(parameterCount)
    return KotlinTypeFactory.simpleNotNullType(annotations.toDefaultAttributes(), classDescriptor, arguments)
}

private val KOTLIN_BUILTIN_ENUM_FUNCTIONS = listOf(FqName("kotlin.enumValues"), FqName("kotlin.enumValueOf"))

fun KtElement.isReferenceToBuiltInEnumFunction(): Boolean {
    return when (this) {
        is KtTypeReference -> {
            val target = (parent.getStrictParentOfType<KtTypeArgumentList>() ?: this)
                .getParentOfTypes(true, KtCallExpression::class.java, KtCallableDeclaration::class.java)
            when (target) {
                is KtCallExpression -> target.isCalling(KOTLIN_BUILTIN_ENUM_FUNCTIONS)
                is KtCallableDeclaration -> {
                    target.anyDescendantOfType<KtCallExpression> {
                        val context = it.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
                        it.isCalling(KOTLIN_BUILTIN_ENUM_FUNCTIONS, context) && it.isUsedAsExpression(context)
                    }
                }

                else -> false
            }
        }

        is KtQualifiedExpression -> {
            var target: KtQualifiedExpression = this
            while (target.callExpression == null) target = target.parent as? KtQualifiedExpression ?: break
            target.callExpression?.calleeExpression?.text in ENUM_STATIC_METHODS
        }

        is KtCallExpression -> this.calleeExpression?.text in ENUM_STATIC_METHODS
        is KtCallableReferenceExpression -> this.callableReference.text in ENUM_STATIC_METHODS
        else -> false
    }
}

val CallableDescriptor.isInvokeOperator: Boolean
    get() = this is FunctionDescriptor && this !is FunctionInvokeDescriptor && isOperator && name == OperatorNameConventions.INVOKE

fun KtCallExpression.canBeReplacedWithInvokeCall(): Boolean {
    return resolveToCall()?.canBeReplacedWithInvokeCall() == true
}

fun ResolvedCall<out CallableDescriptor>.canBeReplacedWithInvokeCall(): Boolean {
    val descriptor = resultingDescriptor as? SimpleFunctionDescriptor ?: return false
    return (descriptor is FunctionInvokeDescriptor || descriptor.isInvokeOperator) && !descriptor.isExtension
}

fun CallableDescriptor.receiverType(): KotlinType? = (dispatchReceiverParameter ?: extensionReceiverParameter)?.type

fun BuilderByPattern<KtExpression>.appendCallOrQualifiedExpression(
    call: KtCallExpression,
    newFunctionName: String
) {
    val callOrQualified = call.getQualifiedExpressionForSelector() ?: call
    if (callOrQualified is KtQualifiedExpression) {
        appendExpression(callOrQualified.receiverExpression)
        if (callOrQualified is KtSafeQualifiedExpression) appendFixedText("?")
        appendFixedText(".")
    }
    appendNonFormattedText(newFunctionName)
    call.valueArgumentList?.let { appendNonFormattedText(it.text) }
    call.lambdaArguments.firstOrNull()?.let {
        if (it.getArgumentExpression() is KtLabeledExpression) appendFixedText(" ")
        appendNonFormattedText(it.text)
    }
}

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

fun ClassDescriptor.isRange(): Boolean {
    return rangeTypes.any { this.fqNameUnsafe.asString() == it }
}

fun KtTypeReference?.typeArguments(): List<KtTypeProjection> {
    return (this?.typeElement as? KtUserType)?.typeArguments.orEmpty()
}
