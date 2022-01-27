// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInsight.Nullability
import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeBinOp
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.types.DfTypes
import com.intellij.codeInspection.dataFlow.value.RelationType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.*

internal fun KotlinType?.toDfType(context: PsiElement) : DfType {
    if (this == null) return DfType.TOP
    if (canBeNull()) {
        var notNullableType = makeNotNullable().toDfTypeNotNull(context)
        if (notNullableType is DfPrimitiveType) {
            notNullableType = SpecialField.UNBOX.asDfType(notNullableType)
                .meet(DfTypes.typedObject(toPsiType(context), Nullability.UNKNOWN))
        }
        return if (notNullableType is DfReferenceType) {
            notNullableType.dropNullability().meet(DfaNullability.NULLABLE.asDfType())
        } else {
            notNullableType
        }
    }
    return toDfTypeNotNull(context)
}

private fun KotlinType.toDfTypeNotNull(context: PsiElement): DfType {
    return when {
        isBoolean() -> DfTypes.BOOLEAN
        isByte() -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
        isChar() -> DfTypes.intRange(LongRangeSet.range(Character.MIN_VALUE.toLong(), Character.MAX_VALUE.toLong()))
        isShort() -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
        isInt() -> DfTypes.INT
        isLong() -> DfTypes.LONG
        isFloat() -> DfTypes.FLOAT
        isDouble() -> DfTypes.DOUBLE
        else -> DfTypes.typedObject(toPsiType(context), Nullability.NOT_NULL)
    }
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
        is CharValue -> DfTypes.intValue(constant.value.toInt())
        is IntValue -> DfTypes.intValue(constant.value)
        is LongValue -> DfTypes.longValue(constant.value)
        is FloatValue -> DfTypes.floatValue(constant.value)
        is DoubleValue -> DfTypes.doubleValue(constant.value)
        else -> DfType.TOP
    }
}

internal fun KtExpression.getKotlinType(): KotlinType? = safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL).getType(this)

internal fun KotlinType.toPsiType(context: PsiElement): PsiType? {
    val typeFqName = this.constructor.declarationDescriptor?.fqNameSafe?.asString()
    val boxed = isMarkedNullable
    fun PsiPrimitiveType.orBoxed() = if (boxed) getBoxedType(context) else this
    return when (typeFqName) {
        "kotlin.Int" -> PsiType.INT.orBoxed()
        "kotlin.Long" -> PsiType.LONG.orBoxed()
        "kotlin.Short" -> PsiType.SHORT.orBoxed()
        "kotlin.Boolean" -> PsiType.BOOLEAN.orBoxed()
        "kotlin.Byte" -> PsiType.BYTE.orBoxed()
        "kotlin.Char" -> PsiType.CHAR.orBoxed()
        "kotlin.Double" -> PsiType.DOUBLE.orBoxed()
        "kotlin.Float" -> PsiType.FLOAT.orBoxed()
        "kotlin.Unit" -> PsiType.VOID.orBoxed()
        "kotlin.String" -> PsiType.getJavaLangString(context.manager, context.resolveScope)
        else -> null
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