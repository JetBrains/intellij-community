// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.DfaNullability
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.types.DfReferenceType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtThisExpression

class KtThisDescriptor internal constructor(val dfType: DfType, val classDef: KtClassDef?, val contextName: String? = null)
    : KtBaseDescriptor {
    internal constructor(classDef: KtClassDef, contextName: String? = null) : 
            this(TypeConstraints.exactClass(classDef).instanceOf().asDfType(), classDef, contextName)

    override fun isStable(): Boolean = true

    override fun isImplicitReadPossible(): Boolean = true

    override fun getDfType(qualifier: DfaVariableValue?): DfType = dfType

    override fun equals(other: Any?): Boolean = other is KtThisDescriptor && other.dfType == dfType

    override fun hashCode(): Int = dfType.hashCode()

    override fun toString(): String {
        val receiver = if (dfType is DfReferenceType)
            dfType.constraint.toString() + if (dfType.nullability == DfaNullability.NULLABLE) "?" else ""
        else
            dfType.toString()
        return "$receiver.this"
    }

    override fun createValue(factory: DfaValueFactory, qualifier: DfaValue?): DfaValue {
        if (qualifier != null) return factory.unknown
        return factory.varFactory.createVariableValue(this)
    }

    override fun isInlineClassReference(): Boolean = classDef?.inline ?: false

    companion object {
        context(KaSession)
        fun descriptorFromThis(expr: KtThisExpression): Pair<VariableDescriptor?, KaType?> {
            val exprType = expr.getKotlinType()
            val symbol = ((expr.instanceReference as? KtNameReferenceExpression)?.reference as? KtReference)?.resolveToSymbol()
            val declType: KaType?
            if (symbol is KaReceiverParameterSymbol && exprType != null) {
                val function = symbol.psi as? KtFunctionLiteral
                declType = symbol.returnType
                if (function != null) {
                    return KtLambdaThisVariableDescriptor(function, declType.toDfType()) to declType
                } else {
                    val dfType = declType.toDfType()
                    val classDef = declType.expandedSymbol?.classDef()
                    return KtThisDescriptor(dfType, classDef, symbol.owningCallableSymbol.name?.asString()) to declType
                }
            }
            else if (symbol is KaClassSymbol && exprType != null) {
                return KtThisDescriptor(symbol.classDef()) to null
            }
            return null to null
        }
    }
}