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
import com.intellij.psi.PsiTypes
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.arrayElementType
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.hasFlexibleNullability
import org.jetbrains.kotlin.analysis.api.components.isArrayOrPrimitiveArray
import org.jetbrains.kotlin.analysis.api.components.isClassType
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isPrimitive
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.withNullability
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

context(_: KaSession)
internal fun KaType?.toDfType(): DfType {
    if (this == null) return DfType.TOP
    if (canBeNull()) {
        var notNullableType = this.withNullability(false).toDfTypeNotNullable()
        if (notNullableType is DfPrimitiveType) {
            val cls = (this as? KaClassType)?.expandedSymbol
            val boxedType = if (cls != null) {
                TypeConstraints.exactClass(cls.classDef()).asDfType()
            } else {
                DfTypes.OBJECT_OR_NULL
            }
            notNullableType = SpecialField.UNBOX.asDfType(notNullableType).meet(boxedType)
        }
        return when (notNullableType) {
            is DfReferenceType -> notNullableType.dropNullability().meet(DfaNullability.NULLABLE.asDfType())
            DfType.BOTTOM -> DfTypes.NULL
            else -> notNullableType
        }
    }
    return toDfTypeNotNullable()
}

context(_: KaSession)
private fun KaType.toDfTypeNotNullable(): DfType {
    return when (this) {
        is KaClassType -> {
            // TODO: anonymous objects
            when (classId) {
                DefaultTypeClassIds.BOOLEAN -> DfTypes.BOOLEAN
                DefaultTypeClassIds.BYTE -> DfTypes.intRange(LongRangeSet.range(Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong()))
                DefaultTypeClassIds.CHAR -> DfTypes.intRange(
                    LongRangeSet.range(Character.MIN_VALUE.code.toLong(), Character.MAX_VALUE.code.toLong())
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
                            val symbol = expandedSymbol ?: return DfType.TOP
                            val classDef = symbol.classDef()
                            val constraint = if (symbol.classKind == KaClassKind.OBJECT) {
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

        is KaTypeParameterType -> symbol.upperBounds.map { type -> type.toDfType() }.fold(DfType.TOP, DfType::meet)
        is KaIntersectionType -> conjuncts.map { type -> type.toDfType() }.fold(DfType.TOP, DfType::meet)
        else -> DfType.TOP
    }
}

context(_: KaSession)
internal fun KaVariableSymbol.toSpecialField(): SpecialField? {
    if (this !is KaPropertySymbol) return null
    val name = name.asString()
    if (name != "size" && name != "length" && name != "ordinal") return null
    val classSymbol = containingDeclaration as? KaNamedClassSymbol ?: return null
    val field = SpecialField.fromQualifierType(classSymbol.defaultType.toDfType()) ?: return null
    val expectedFieldName = if (field == SpecialField.ARRAY_LENGTH) "size" else field.toString()
    if (name != expectedFieldName) return null
    return field
}

context(_: KaSession)
internal fun KtExpression.getKotlinType(): KaType? {
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
        val call = resolveToCall()?.singleFunctionCallOrNull()
        if (call != null) {
            val functionReturnType = (call.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol)?.returnType
            if (functionReturnType is KaTypeParameterType) {
                val upperBound = functionReturnType.symbol.upperBounds.singleOrNull()
                if (upperBound != null) {
                    return upperBound
                }
            }
        }
    }
    return expressionType
}

context(_: KaSession)
internal fun KaType.getJvmAwareArrayElementType(): KaType? {
    if (!isArrayOrPrimitiveArray) return null
    val type = arrayElementType ?: return null
    if (this.isClassType(StandardClassIds.Array) && type.isPrimitive) {
        return type.withNullability(true)
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

internal fun mathOpFromAssignmentToken(token: IElementType): LongRangeBinOp? = when (token) {
    KtTokens.PLUSEQ -> LongRangeBinOp.PLUS
    KtTokens.MINUSEQ -> LongRangeBinOp.MINUS
    KtTokens.MULTEQ -> LongRangeBinOp.MUL
    KtTokens.DIVEQ -> LongRangeBinOp.DIV
    KtTokens.PERCEQ -> LongRangeBinOp.MOD
    else -> null
}

context(_: KaSession)
internal fun KaType.canBeNull() = isMarkedNullable || hasFlexibleNullability

context(_: KaSession)
internal fun getConstant(expr: KtConstantExpression): DfType {
    val type = expr.expressionType
    val constant: KaConstantValue? = if (type == null) null else expr.evaluate()
    return when (constant) {
        is KaConstantValue.NullValue -> DfTypes.NULL
        is KaConstantValue.BooleanValue -> DfTypes.booleanValue(constant.value)
        is KaConstantValue.ByteValue -> DfTypes.intValue(constant.value.toInt())
        is KaConstantValue.ShortValue -> DfTypes.intValue(constant.value.toInt())
        is KaConstantValue.CharValue -> DfTypes.intValue(constant.value.code)
        is KaConstantValue.IntValue -> DfTypes.intValue(constant.value)
        is KaConstantValue.LongValue -> DfTypes.longValue(constant.value)
        is KaConstantValue.FloatValue -> DfTypes.floatValue(constant.value)
        is KaConstantValue.DoubleValue -> DfTypes.doubleValue(constant.value)
        else -> DfType.TOP
    }
}

context(_: KaSession)
internal fun getInlineableLambda(expr: KtCallExpression): LambdaAndParameter? {
    val lambdaArgument = expr.lambdaArguments.singleOrNull() ?: return null
    val lambdaExpression = lambdaArgument.getLambdaExpression() ?: return null
    val index = expr.valueArguments.indexOf(lambdaArgument)
    assert(index >= 0)
    val resolvedCall = expr.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val symbol = resolvedCall.partiallyAppliedSymbol.symbol as? KaNamedFunctionSymbol
    if (symbol == null || !symbol.isInline) return null
    val parameterSymbol = resolvedCall.argumentMapping[lambdaExpression]?.symbol ?: return null
    if (parameterSymbol.isNoinline) return null
    return LambdaAndParameter(lambdaExpression, parameterSymbol)
}

internal data class LambdaAndParameter(val lambda: KtLambdaExpression, val descriptor: KaValueParameterSymbol)
