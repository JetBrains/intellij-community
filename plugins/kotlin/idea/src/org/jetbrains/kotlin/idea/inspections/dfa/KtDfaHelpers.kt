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
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

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
            FqNames._char -> DfTypes.intRange(LongRangeSet.range(Character.MIN_VALUE.toLong(), Character.MAX_VALUE.toLong()))
            FqNames._short -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
            FqNames._int -> DfTypes.INT
            FqNames._long -> DfTypes.LONG
            FqNames._float -> DfTypes.FLOAT
            FqNames._double -> DfTypes.DOUBLE
            FqNames.array ->
                TypeConstraints.instanceOf(toPsiType(context) ?: return DfType.TOP).asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            FqNames.any -> DfTypes.NOT_NULL_OBJECT
            else -> {
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
                        TypeConstraints.exactClass(psiClass).instanceOf()
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
    val bindingContext = expr.analyze(BodyResolveMode.PARTIAL)
    val type = bindingContext.getType(expr)
    val constant: ConstantValue<Any?>? =
        if (type == null) null else ConstantExpressionEvaluator.getConstant(expr, bindingContext)?.toConstantValue(type)
    return when (constant) {
        is NullValue -> DfTypes.NULL
        is BooleanValue -> DfTypes.booleanValue(constant.value)
        is ByteValue -> DfTypes.intValue(constant.value.toInt())
        is ShortValue -> DfTypes.intValue(constant.value.toInt())
        is CharValue -> DfTypes.intValue(constant.value.toInt())
        is IntValue -> DfTypes.intValue(constant.value)
        is LongValue -> DfTypes.longValue(constant.value)
        is FloatValue -> DfTypes.floatValue(constant.value)
        is DoubleValue -> DfTypes.doubleValue(constant.value)
        else -> DfType.TOP
    }
}

internal fun KtExpression.getKotlinType(): KotlinType? = analyze(BodyResolveMode.PARTIAL).getType(this)

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
        FqNames.unit -> PsiType.VOID.orBoxed()
        FqNames.array -> context.builtIns.getArrayElementType(this).toPsiType(context)?.createArrayType()
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