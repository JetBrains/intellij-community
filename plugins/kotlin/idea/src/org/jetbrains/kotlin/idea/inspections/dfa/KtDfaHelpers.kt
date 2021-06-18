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
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.lexer.KtTokens
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
        var notNullableType = makeNotNullable().toDfType(context)
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
    return when (val descriptor = this.constructor.declarationDescriptor) {
        is TypeAliasDescriptor -> {
            descriptor.expandedType.toDfType(context)
        }
        is ClassDescriptor -> when (val fqNameUnsafe = descriptor.fqNameUnsafe) {
            StandardNames.FqNames._boolean -> DfTypes.BOOLEAN
            StandardNames.FqNames._byte -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
            StandardNames.FqNames._char -> DfTypes.intRange(LongRangeSet.range(Character.MIN_VALUE.toLong(), Character.MAX_VALUE.toLong()))
            StandardNames.FqNames._short -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
            StandardNames.FqNames._int -> DfTypes.INT
            StandardNames.FqNames._long -> DfTypes.LONG
            StandardNames.FqNames._float -> DfTypes.FLOAT
            StandardNames.FqNames._double -> DfTypes.DOUBLE
            StandardNames.FqNames.array ->
                TypeConstraints.instanceOf(toPsiType(context) ?: return DfType.TOP).asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            StandardNames.FqNames.any -> DfTypes.NOT_NULL_OBJECT
            else -> {
                val typeFqName = when(fqNameUnsafe) {
                    StandardNames.FqNames.string -> CommonClassNames.JAVA_LANG_STRING
                    else -> fqNameUnsafe.asString()
                }
                val psiClass = JavaPsiFacade.getInstance(context.project).findClass(typeFqName, context.resolveScope) ?: return DfType.TOP
                return TypeConstraints.exactClass(psiClass).instanceOf().asDfType().meet(DfTypes.NOT_NULL_OBJECT)
            }
        }
        // TODO: Support type parameters
        else -> DfType.TOP
    }
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
    val typeFqName = this.constructor.declarationDescriptor?.fqNameUnsafe
    val boxed = canBeNull()
    fun PsiPrimitiveType.orBoxed() = if (boxed) getBoxedType(context) else this
    return when (typeFqName) {
        StandardNames.FqNames._int -> PsiType.INT.orBoxed()
        StandardNames.FqNames._long -> PsiType.LONG.orBoxed()
        StandardNames.FqNames._short -> PsiType.SHORT.orBoxed()
        StandardNames.FqNames._boolean -> PsiType.BOOLEAN.orBoxed()
        StandardNames.FqNames._byte -> PsiType.BYTE.orBoxed()
        StandardNames.FqNames._char -> PsiType.CHAR.orBoxed()
        StandardNames.FqNames._double -> PsiType.DOUBLE.orBoxed()
        StandardNames.FqNames._float -> PsiType.FLOAT.orBoxed()
        StandardNames.FqNames.unit -> PsiType.VOID.orBoxed()
        StandardNames.FqNames.string -> PsiType.getJavaLangString(context.manager, context.resolveScope)
        StandardNames.FqNames.array -> context.builtIns.getArrayElementType(this).toPsiType(context)?.createArrayType()
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