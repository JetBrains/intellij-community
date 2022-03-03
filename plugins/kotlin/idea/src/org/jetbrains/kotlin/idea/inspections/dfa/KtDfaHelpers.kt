// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames.FqNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqNameUnsafe
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
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import org.jetbrains.kotlin.types.typeUtil.makeNullable

internal fun KotlinType?.toDfType(context: KtElement) : DfType {
    if (this == null) return DfType.TOP
    if (canBeNull()) {
        var notNullableType = makeNotNullable().toDfTypeNotNullable(context)
        if (notNullableType is DfPrimitiveType) {
            notNullableType = SpecialField.UNBOX.asDfType(notNullableType)
                .meet(DfTypes.typedObject(toPsiType(context), Nullability.UNKNOWN))
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
    return toDfTypeNotNullable(context)
}

private fun KotlinType.toDfTypeNotNullable(context: KtElement): DfType {
    return when (val descriptor = this.constructor.declarationDescriptor) {
        is TypeAliasDescriptor -> {
            descriptor.expandedType.toDfType(context)
        }
        is ClassDescriptor -> when (val fqNameUnsafe = descriptor.fqNameUnsafe) {
            FqNames._boolean -> DfTypes.BOOLEAN
            FqNames._byte -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
            FqNames._char -> DfTypes.intRange(LongRangeSet.range(Character.MIN_VALUE.code.toLong(), Character.MAX_VALUE.code.toLong()))
            FqNames._short -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
            FqNames._int -> DfTypes.INT
            FqNames._long -> DfTypes.LONG
            FqNames._float -> DfTypes.FLOAT
            FqNames._double -> DfTypes.DOUBLE
            FqNames.array ->
                TypeConstraints.instanceOf(toPsiType(context) ?: return DfType.TOP).asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            FqNames.any -> DfTypes.NOT_NULL_OBJECT
            else -> {
                if (fqNameUnsafe.shortNameOrSpecial().isSpecial) {
                    val source = descriptor.source
                    if (source is KotlinSourceElement) {
                        val psi = source.psi
                        if (psi is KtObjectDeclaration) {
                            val bindingContext = psi.safeAnalyzeNonSourceRootCode()
                            val superTypes = psi.superTypeListEntries
                                .map { entry ->
                                    val psiType = entry.typeReference?.getAbbreviatedTypeOrType(bindingContext)?.toPsiType(psi)
                                    PsiUtil.resolveClassInClassTypeOnly(psiType)
                                }
                            return if (superTypes.contains(null))
                                DfType.TOP
                            else
                                TypeConstraints.exactSubtype(psi, superTypes).asDfType().meet(DfTypes.NOT_NULL_OBJECT)
                        }
                    }
                }
                val typeConstraint = when (val typeFqName = correctFqName(fqNameUnsafe)) {
                    "kotlin.ByteArray" -> TypeConstraints.exact(PsiType.BYTE.createArrayType())
                    "kotlin.IntArray" -> TypeConstraints.exact(PsiType.INT.createArrayType())
                    "kotlin.LongArray" -> TypeConstraints.exact(PsiType.LONG.createArrayType())
                    "kotlin.ShortArray" -> TypeConstraints.exact(PsiType.SHORT.createArrayType())
                    "kotlin.CharArray" -> TypeConstraints.exact(PsiType.CHAR.createArrayType())
                    "kotlin.BooleanArray" -> TypeConstraints.exact(PsiType.BOOLEAN.createArrayType())
                    "kotlin.FloatArray" -> TypeConstraints.exact(PsiType.FLOAT.createArrayType())
                    "kotlin.DoubleArray" -> TypeConstraints.exact(PsiType.DOUBLE.createArrayType())
                    else -> {
                        val psiClass =
                            JavaPsiFacade.getInstance(context.project).findClass(typeFqName, context.resolveScope) ?: return DfType.TOP
                        if (descriptor.kind == ClassKind.OBJECT && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
                            TypeConstraints.singleton(psiClass)
                        } else {
                            TypeConstraints.exactClass(psiClass).instanceOf()
                        }
                    }
                }
                return typeConstraint.asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            }
        }
        // TODO: Support type parameters
        else -> DfType.TOP
    }
}

// see org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap in kotlin compiler
private fun correctFqName(fqNameUnsafe: FqNameUnsafe) = when (val rawName = fqNameUnsafe.asString()) {
    "kotlin.Any" -> CommonClassNames.JAVA_LANG_OBJECT
    "kotlin.String" -> CommonClassNames.JAVA_LANG_STRING
    "kotlin.CharSequence" -> CommonClassNames.JAVA_LANG_CHAR_SEQUENCE
    "kotlin.Throwable" -> CommonClassNames.JAVA_LANG_THROWABLE
    "kotlin.Cloneable" -> CommonClassNames.JAVA_LANG_CLONEABLE
    "kotlin.Number" -> CommonClassNames.JAVA_LANG_NUMBER
    "kotlin.Comparable" -> CommonClassNames.JAVA_LANG_COMPARABLE
    "kotlin.Enum" -> CommonClassNames.JAVA_LANG_ENUM
    "kotlin.Annotation" -> CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION
    "kotlin.Nothing" -> CommonClassNames.JAVA_LANG_VOID
    "kotlin.collections.Iterable", "kotlin.collections.MutableIterable" -> CommonClassNames.JAVA_LANG_ITERABLE
    "kotlin.collections.Iterator", "kotlin.collections.MutableIterator" -> CommonClassNames.JAVA_UTIL_ITERATOR
    "kotlin.collections.Collection", "kotlin.collections.MutableCollection" -> CommonClassNames.JAVA_UTIL_COLLECTION
    "kotlin.collections.List", "kotlin.collections.MutableList" -> CommonClassNames.JAVA_UTIL_LIST
    "kotlin.collections.ListIterator", "kotlin.collections.MutableListIterator" -> "java.util.ListIterator"
    "kotlin.collections.Set", "kotlin.collections.MutableSet" -> CommonClassNames.JAVA_UTIL_SET
    "kotlin.collections.Map", "kotlin.collections.MutableMap" -> CommonClassNames.JAVA_UTIL_MAP
    "kotlin.collections.MapEntry", "kotlin.collections.MutableMapEntry" -> CommonClassNames.JAVA_UTIL_MAP_ENTRY
    else -> rawName
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
internal fun KotlinType.getArrayElementType(context: KtElement): KotlinType? {
    if (!KotlinBuiltIns.isArrayOrPrimitiveArray(this)) return null
    val type = context.builtIns.getArrayElementType(this)
    if (KotlinBuiltIns.isArray(this) && KotlinBuiltIns.isPrimitiveType(type)) {
        return type.makeNullable()
    }
    return type
}

internal fun KotlinType.toPsiType(context: KtElement): PsiType? {
    val typeFqName = this.constructor.declarationDescriptor?.fqNameUnsafe ?: return null
    val boxed = canBeNull()
    fun PsiPrimitiveType.orBoxed() = if (boxed) getBoxedType(context) else this
    return when (typeFqName) {
        FqNames._int -> PsiType.INT.orBoxed()
        FqNames._long -> PsiType.LONG.orBoxed()
        FqNames._short -> PsiType.SHORT.orBoxed()
        FqNames._boolean -> PsiType.BOOLEAN.orBoxed()
        FqNames._byte -> PsiType.BYTE.orBoxed()
        FqNames._char -> PsiType.CHAR.orBoxed()
        FqNames._double -> PsiType.DOUBLE.orBoxed()
        FqNames._float -> PsiType.FLOAT.orBoxed()
        FqNames.nothing -> PsiType.VOID.orBoxed()
        FqNames.array -> getArrayElementType(context)?.toPsiType(context)?.createArrayType()
        else -> when (val fqNameString = correctFqName(typeFqName)) {
            "kotlin.ByteArray" -> PsiType.BYTE.createArrayType()
            "kotlin.IntArray" -> PsiType.INT.createArrayType()
            "kotlin.LongArray" -> PsiType.LONG.createArrayType()
            "kotlin.ShortArray" -> PsiType.SHORT.createArrayType()
            "kotlin.CharArray" -> PsiType.CHAR.createArrayType()
            "kotlin.BooleanArray" -> PsiType.BOOLEAN.createArrayType()
            "kotlin.FloatArray" -> PsiType.FLOAT.createArrayType()
            "kotlin.DoubleArray" -> PsiType.DOUBLE.createArrayType()
            else -> JavaPsiFacade.getElementFactory(context.project).createTypeByFQClassName(fqNameString, context.resolveScope)
        }
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