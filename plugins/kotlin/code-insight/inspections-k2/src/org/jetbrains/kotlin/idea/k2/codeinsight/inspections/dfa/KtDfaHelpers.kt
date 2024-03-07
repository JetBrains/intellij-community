// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

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
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.analysis.api.types.KtIntersectionType
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

context(KtAnalysisSession)
internal fun KtType?.toDfType(): DfType {
    if (this == null) return DfType.TOP
    if (canBeNull()) {
        var notNullableType = this.withNullability(KtTypeNullability.NON_NULLABLE).toDfTypeNotNullable()
        if (notNullableType is DfPrimitiveType) {
            val cls = (this as? KtNonErrorClassType)?.expandedClassSymbol
            val boxedType: DfType
            if (cls != null) {
                boxedType = TypeConstraints.exactClass(cls.classDef()).asDfType()
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

context(KtAnalysisSession)
private fun KtType.toDfTypeNotNullable(): DfType {
    return when (this) {
        is KtNonErrorClassType -> {
            // TODO: anonymous objects
            when(classId) {
                DefaultTypeClassIds.BOOLEAN -> DfTypes.BOOLEAN
                DefaultTypeClassIds.BYTE -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
                DefaultTypeClassIds.CHAR -> DfTypes.intRange(
                    LongRangeSet.range(
                        Character.MIN_VALUE.code.toLong(),
                        Character.MAX_VALUE.code.toLong()
                    )
                )

                DefaultTypeClassIds.SHORT -> DfTypes.intRange(LongRangeSet.range(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong()))
                DefaultTypeClassIds.INT -> DfTypes.INT
                DefaultTypeClassIds.LONG -> DfTypes.LONG
                DefaultTypeClassIds.FLOAT -> DfTypes.FLOAT
                DefaultTypeClassIds.DOUBLE -> DfTypes.DOUBLE
                DefaultTypeClassIds.ANY -> DfTypes.NOT_NULL_OBJECT
                StandardClassIds.Array -> {
                    val elementDfType = getJvmAwareArrayElementType()?.toDfType() as? DfReferenceType
                    val elementConstraint = elementDfType?.constraint ?: TypeConstraints.TOP
                    elementConstraint.arrayOf().asDfType().meet(DfTypes.NOT_NULL_OBJECT)
                }
                else -> {
                    val primitiveArrayElementType = StandardClassIds.elementTypeByPrimitiveArrayType[classId]
                    when (primitiveArrayElementType) {
                        DefaultTypeClassIds.BYTE -> TypeConstraints.exact(PsiTypes.byteType().createArrayType()).asDfType()
                        DefaultTypeClassIds.SHORT -> TypeConstraints.exact(PsiTypes.shortType().createArrayType()).asDfType()
                        DefaultTypeClassIds.INT -> TypeConstraints.exact(PsiTypes.intType().createArrayType()).asDfType()
                        DefaultTypeClassIds.LONG -> TypeConstraints.exact(PsiTypes.longType().createArrayType()).asDfType()
                        DefaultTypeClassIds.FLOAT -> TypeConstraints.exact(PsiTypes.floatType().createArrayType()).asDfType()
                        DefaultTypeClassIds.DOUBLE -> TypeConstraints.exact(PsiTypes.doubleType().createArrayType()).asDfType()
                        DefaultTypeClassIds.CHAR -> TypeConstraints.exact(PsiTypes.charType().createArrayType()).asDfType()
                        DefaultTypeClassIds.BOOLEAN -> TypeConstraints.exact(PsiTypes.booleanType().createArrayType()).asDfType()
                        else -> {
                            val symbol = expandedClassSymbol ?: return DfType.TOP
                            val classDef = symbol.classDef()
                            val constraint = if (symbol.classKind == KtClassKind.OBJECT) {
                                TypeConstraints.singleton(classDef)
                            } else {
                                TypeConstraints.exactClass(classDef).instanceOf()
                            }
                            constraint.asDfType().meet(DfTypes.NOT_NULL_OBJECT)
                        }
                    }
                }
            }
        }
        is KtTypeParameterType -> symbol.upperBounds.map { type -> type.toDfType() }.fold(DfType.TOP, DfType::meet)
        is KtIntersectionType -> conjuncts.map { type -> type.toDfType() }.fold(DfType.TOP, DfType::meet)
        else -> DfType.TOP
    }
}

context(KtAnalysisSession)
internal fun KtExpression.getKotlinType(): KtType? {
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
        val call = resolveCall()?.singleFunctionCallOrNull()
        if (call != null) {
            val functionReturnType = (call.partiallyAppliedSymbol.symbol as? KtFunctionSymbol)?.returnType
            if (functionReturnType is KtTypeParameterType) {
                val upperBound = functionReturnType.symbol.upperBounds.singleOrNull()
                if (upperBound != null) {
                    return upperBound
                }
            }
        }
    }
    return getKtType()
}

context(KtAnalysisSession)
internal fun KtType.getJvmAwareArrayElementType(): KtType? {
    if (!isArrayOrPrimitiveArray()) return null
    val type = getArrayElementType() ?: return null
    if (this.isClassTypeWithClassId(StandardClassIds.Array) && type.isPrimitive) {
        return type.withNullability(KtTypeNullability.NULLABLE)
    }
    return type
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

context(KtAnalysisSession)
internal fun KtType.toPsiPrimitiveType(): PsiPrimitiveType = when ((this as? KtNonErrorClassType)?.classId) {
    DefaultTypeClassIds.BOOLEAN -> PsiTypes.booleanType()
    DefaultTypeClassIds.BYTE -> PsiTypes.byteType()
    DefaultTypeClassIds.CHAR -> PsiTypes.charType()
    DefaultTypeClassIds.SHORT -> PsiTypes.shortType()
    DefaultTypeClassIds.INT -> PsiTypes.intType()
    DefaultTypeClassIds.LONG -> PsiTypes.longType()
    DefaultTypeClassIds.FLOAT -> PsiTypes.floatType()
    DefaultTypeClassIds.DOUBLE -> PsiTypes.doubleType()
    else -> throw IllegalArgumentException("Not a primitive analog: $this")
}

context(KtAnalysisSession)
internal fun KtType.canBeNull() = isMarkedNullable || hasFlexibleNullability

context(KtAnalysisSession)
internal fun getConstant(expr: KtConstantExpression): DfType {
    val type = expr.getKtType()
    val constant: KtConstantValue? = if (type == null) null else expr.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)
    return when (constant) {
        is KtConstantValue.KtNullConstantValue -> DfTypes.NULL
        is KtConstantValue.KtBooleanConstantValue -> DfTypes.booleanValue(constant.value)
        is KtConstantValue.KtByteConstantValue -> DfTypes.intValue(constant.value.toInt())
        is KtConstantValue.KtShortConstantValue -> DfTypes.intValue(constant.value.toInt())
        is KtConstantValue.KtCharConstantValue -> DfTypes.intValue(constant.value.code)
        is KtConstantValue.KtIntConstantValue -> DfTypes.intValue(constant.value)
        is KtConstantValue.KtLongConstantValue -> DfTypes.longValue(constant.value)
        is KtConstantValue.KtFloatConstantValue -> DfTypes.floatValue(constant.value)
        is KtConstantValue.KtDoubleConstantValue -> DfTypes.doubleValue(constant.value)
        else -> DfType.TOP
    }
}

context(KtAnalysisSession)
internal fun getInlineableLambda(expr: KtCallExpression): LambdaAndParameter? {
    val lambdaArgument = expr.lambdaArguments.singleOrNull() ?: return null
    val lambdaExpression = lambdaArgument.getLambdaExpression() ?: return null
    val index = expr.valueArguments.indexOf(lambdaArgument)
    assert(index >= 0)
    val resolvedCall = expr.resolveCall()?.singleFunctionCallOrNull() ?: return null
    val symbol = resolvedCall.partiallyAppliedSymbol.symbol as? KtFunctionSymbol
    if (symbol == null || !symbol.isInline) return null
    val parameterSymbol = resolvedCall.argumentMapping[lambdaExpression]?.symbol ?: return null
    if (parameterSymbol.isNoinline) return null
    return LambdaAndParameter(lambdaExpression, parameterSymbol)
}

internal data class LambdaAndParameter(val lambda: KtLambdaExpression, val descriptor: KtValueParameterSymbol)
