// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames.FqNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.KotlinSourceElement
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.makeNullable

internal fun KotlinType?.toDfType(): DfType {
    if (this == null) return DfType.TOP
    if (canBeNull()) {
        var notNullableType = makeNotNullable().toDfTypeNotNullable()
        if (notNullableType is DfPrimitiveType) {
            val cls = this.constructor.declarationDescriptor as? ClassDescriptor
            val boxedType: DfType
            if (cls != null) {
                boxedType = TypeConstraints.exactClass(KtClassDef(cls)).asDfType()
            } else {
                boxedType = DfTypes.OBJECT_OR_NULL
            }
            notNullableType = SpecialField.UNBOX.asDfType(notNullableType).meet(boxedType)
        }
        return when (notNullableType) {
            is DfReferenceType -> {
                notNullableType.dropNullability().meet(DfaNullability.NULLABLE.asDfType())
            }
            DfType.BOTTOM -> {
                DfTypes.NULL
            }
            else -> {
                notNullableType
            }
        }
    }
    return toDfTypeNotNullable()
}

private fun KotlinType.toDfTypeNotNullable(): DfType {
    return when (val descriptor = this.constructor.declarationDescriptor) {
        is TypeAliasDescriptor -> {
            descriptor.expandedType.toDfType()
        }
        is ClassDescriptor -> when (descriptor.fqNameUnsafe) {
            FqNames._boolean -> DfTypes.BOOLEAN
            FqNames._byte -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
            FqNames._char -> DfTypes.intRange(LongRangeSet.range(Character.MIN_VALUE.code.toLong(), Character.MAX_VALUE.code.toLong()))
            FqNames._short -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
            FqNames._int -> DfTypes.INT
            FqNames._long -> DfTypes.LONG
            FqNames._float -> DfTypes.FLOAT
            FqNames._double -> DfTypes.DOUBLE
            FqNames.array -> {
                val elementType = getArrayElementType()?.constructor?.declarationDescriptor as? ClassDescriptor ?: return DfType.TOP
                elementType.getTypeConstraint().arrayOf().asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            }

            FqNames.any -> DfTypes.NOT_NULL_OBJECT
            else -> descriptor.getTypeConstraint().asDfType().meet(DfTypes.NOT_NULL_OBJECT)
        }

        is TypeParameterDescriptor -> descriptor.upperBounds.map { type -> type.toDfType() }.fold(DfType.TOP, DfType::meet)
        else -> DfType.TOP
    }
}

private fun ClassDescriptor.getTypeConstraint(): TypeConstraint {
    val fqNameUnsafe = fqNameUnsafe
    if (fqNameUnsafe.shortNameOrSpecial().isSpecial) {
        val source = source
        if (source is KotlinSourceElement) {
            val psi = source.psi
            if (psi is KtObjectDeclaration) {
                val bindingContext = psi.safeAnalyzeNonSourceRootCode()
                val superTypes: List<KtClassDef?> = psi.superTypeListEntries
                    .map { entry ->
                        val classDescriptor = entry.typeReference?.getAbbreviatedTypeOrType(bindingContext)
                            ?.constructor?.declarationDescriptor as? ClassDescriptor
                        if (classDescriptor == null) null else KtClassDef(classDescriptor)
                    }
                return if (superTypes.contains(null))
                    TypeConstraints.TOP
                else
                    TypeConstraints.exactSubtype(psi, superTypes)
            }
        }
    }
    return when (fqNameUnsafe.asString()) {
        "kotlin.ByteArray" -> TypeConstraints.exact(PsiType.BYTE.createArrayType())
        "kotlin.IntArray" -> TypeConstraints.exact(PsiType.INT.createArrayType())
        "kotlin.LongArray" -> TypeConstraints.exact(PsiType.LONG.createArrayType())
        "kotlin.ShortArray" -> TypeConstraints.exact(PsiType.SHORT.createArrayType())
        "kotlin.CharArray" -> TypeConstraints.exact(PsiType.CHAR.createArrayType())
        "kotlin.BooleanArray" -> TypeConstraints.exact(PsiType.BOOLEAN.createArrayType())
        "kotlin.FloatArray" -> TypeConstraints.exact(PsiType.FLOAT.createArrayType())
        "kotlin.DoubleArray" -> TypeConstraints.exact(PsiType.DOUBLE.createArrayType())
        else -> {
            val classDef = KtClassDef(this)
            if (kind == ClassKind.OBJECT) {
                TypeConstraints.singleton(classDef)
            } else {
                TypeConstraints.exactClass(classDef).instanceOf()
            }
        }
    }
}

internal fun KotlinType?.fqNameEquals(fqName: String): Boolean {
    return this != null && this.constructor.declarationDescriptor?.fqNameUnsafe?.asString() == fqName
}

internal fun KotlinType.canBeNull() = isMarkedNullable || isNullabilityFlexible()

internal fun getConstant(expr: KtConstantExpression): DfType {
    val bindingContext = expr.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    val type = bindingContext.getType(expr)
    val constant: ConstantValue<Any?>? =
        if (type == null) null else ConstantExpressionEvaluator.getConstant(expr, bindingContext)?.toConstantValue(type)
    return when (constant) {
        is NullValue -> DfTypes.NULL
        is BooleanValue -> DfTypes.booleanValue(constant.value)
        is ByteValue -> DfTypes.intValue(constant.value.toInt())
        is ShortValue -> DfTypes.intValue(constant.value.toInt())
        is CharValue -> DfTypes.intValue(constant.value.code)
        is IntValue -> DfTypes.intValue(constant.value)
        is LongValue -> DfTypes.longValue(constant.value)
        is FloatValue -> DfTypes.floatValue(constant.value)
        is DoubleValue -> DfTypes.doubleValue(constant.value)
        else -> DfType.TOP
    }
}

internal fun KtExpression.getKotlinType(): KotlinType? {
    var parent = this.parent
    if (parent is KtDotQualifiedExpression && parent.selectorExpression == this) {
        parent = parent.parent
    }
    while (parent is KtParenthesizedExpression) {
        parent = parent.parent
    }
    // In (call() as? X), the call() type might be inferred to be X due to peculiarities
    // of Kotlin type system. This produces an unpleasant effect for data flow analysis:
    // it assumes that this cast never fails, thus result is never null, which is actually wrong
    // So we have to patch the original call type, widening it to its upper bound.
    // Current implementation is not always precise and may result in skipping a useful warning.
    if (parent is KtBinaryExpressionWithTypeRHS && parent.operationReference.text == "as?") {
        val call = resolveToCall()
        if (call != null) {
            val descriptor = call.resultingDescriptor
            val typeDescriptor = descriptor.original.returnType?.constructor?.declarationDescriptor
            if (typeDescriptor is TypeParameterDescriptor) {
                val upperBound = typeDescriptor.upperBounds.singleOrNull()
                if (upperBound != null) {
                    return upperBound
                }
            }
        }
    }
    return safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL).getType(this)
}

/**
 * JVM-patched array element type (e.g. Int? for Array<Int>)
 */
internal fun KotlinType.getArrayElementType(): KotlinType? {
    if (!KotlinBuiltIns.isArrayOrPrimitiveArray(this)) return null
    val type = builtIns.getArrayElementType(this)
    if (KotlinBuiltIns.isArray(this) && KotlinBuiltIns.isPrimitiveType(type)) {
        return type.makeNullable()
    }
    return type
}

internal fun KotlinType.toPsiPrimitiveType(): PsiPrimitiveType {
    return when (this.constructor.declarationDescriptor?.fqNameUnsafe) {
        FqNames._int -> PsiType.INT
        FqNames._long -> PsiType.LONG
        FqNames._short -> PsiType.SHORT
        FqNames._boolean -> PsiType.BOOLEAN
        FqNames._byte -> PsiType.BYTE
        FqNames._char -> PsiType.CHAR
        FqNames._double -> PsiType.DOUBLE
        FqNames._float -> PsiType.FLOAT
        else -> throw IllegalArgumentException("Not a primitive analog: $this")
    }
}

internal fun relationFromToken(token: IElementType): RelationType? = when (token) {
    KtTokens.LT -> RelationType.LT
    KtTokens.GT -> RelationType.GT
    KtTokens.LTEQ -> RelationType.LE
    KtTokens.GTEQ -> RelationType.GE
    KtTokens.EQEQ -> RelationType.EQ
    KtTokens.EXCLEQ -> RelationType.NE
    KtTokens.EQEQEQ -> RelationType.EQ
    KtTokens.EXCLEQEQEQ -> RelationType.NE
    else -> null
}

internal fun mathOpFromToken(ref: KtOperationReferenceExpression): LongRangeBinOp? =
    when (ref.text) {
        "+" -> LongRangeBinOp.PLUS
        "-" -> LongRangeBinOp.MINUS
        "*" -> LongRangeBinOp.MUL
        "/" -> LongRangeBinOp.DIV
        "%" -> LongRangeBinOp.MOD
        "and" -> LongRangeBinOp.AND
        "or" -> LongRangeBinOp.OR
        "xor" -> LongRangeBinOp.XOR
        "shl" -> LongRangeBinOp.SHL
        "shr" -> LongRangeBinOp.SHR
        "ushr" -> LongRangeBinOp.USHR
        else -> null
    }

internal fun mathOpFromAssignmentToken(token: IElementType): LongRangeBinOp? = when(token) {
    KtTokens.PLUSEQ -> LongRangeBinOp.PLUS
    KtTokens.MINUSEQ -> LongRangeBinOp.MINUS
    KtTokens.MULTEQ -> LongRangeBinOp.MUL
    KtTokens.DIVEQ -> LongRangeBinOp.DIV
    KtTokens.PERCEQ -> LongRangeBinOp.MOD
    else -> null
}

internal fun getInlineableLambda(expr: KtCallExpression): LambdaAndParameter? {
    val lambdaArgument = expr.lambdaArguments.singleOrNull() ?: return null
    val lambdaExpression = lambdaArgument.getLambdaExpression() ?: return null
    val index = expr.valueArguments.indexOf(lambdaArgument)
    assert(index >= 0)
    val resolvedCall = expr.resolveToCall() ?: return null
    val descriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor
    if (descriptor == null || !descriptor.isInline) return null
    val parameterDescriptor = (resolvedCall.getArgumentMapping(lambdaArgument) as? ArgumentMatch)?.valueParameter ?: return null
    if (parameterDescriptor.isNoinline) return null
    return LambdaAndParameter(lambdaExpression, parameterDescriptor)
}

internal data class LambdaAndParameter(val lambda: KtLambdaExpression, val descriptor: ValueParameterDescriptor)