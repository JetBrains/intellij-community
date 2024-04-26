// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.calls.KtImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.calls.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.*

class KtVariableDescriptor(
    val module: KtModule,
    val pointer: KtSymbolPointer<KtVariableLikeSymbol>,
    val type: DfType,
    val hash: Int
) : JvmVariableDescriptor() {
    val stable: Boolean by lazy {
        when (val result = analyze(module) {
            when (val symbol = pointer.restoreSymbol()) {
                is KtValueParameterSymbol, is KtEnumEntrySymbol -> return@analyze true
                is KtPropertySymbol -> return@analyze symbol.isVal
                is KtLocalVariableSymbol -> {
                    if (symbol.isVal) return@analyze true
                    val psiElement = symbol.psi?.parent as? KtElement
                    if (psiElement == null) return@analyze true
                    return@analyze psiElement
                }

                else -> return@analyze false
            }
        }) {
            is Boolean -> result
            is KtElement -> !getVariablesChangedInNestedFunctions(result).contains(this@KtVariableDescriptor)
            else -> false
        }
    }

    override fun isStable(): Boolean = stable

    override fun canBeCapturedInClosure(): Boolean = analyze(module) {
        val symbol = pointer.restoreSymbol() ?: return@analyze false
        return@analyze symbol is KtVariableSymbol && symbol.isVal
    }

    override fun getDfType(qualifier: DfaVariableValue?): DfType = type

    override fun equals(other: Any?): Boolean = other is KtVariableDescriptor && other.pointer.pointsToTheSameSymbolAs(pointer)

    override fun hashCode(): Int = hash

    override fun toString(): String = analyze(module) {
        val symbol = pointer.restoreSymbol() ?: return@analyze "<unknown>"
        symbol.name.asString()
    }

    companion object {
        context(KtAnalysisSession)
        fun getSingleLambdaParameter(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val parameterSymbol = lambda.functionLiteral.getAnonymousFunctionSymbol().valueParameters.singleOrNull() ?: return null
            if ((parameterSymbol.psi as? KtParameter)?.destructuringDeclaration != null) return null
            return factory.varFactory.createVariableValue(parameterSymbol.variableDescriptor())
        }

        context(KtAnalysisSession)
        fun getLambdaReceiver(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val receiverType = (lambda.functionLiteral.getFunctionalType() as? KtFunctionalType)?.receiverType ?: return null
            val descriptor = KtLambdaThisVariableDescriptor(lambda.functionLiteral, receiverType.toDfType())
            return factory.varFactory.createVariableValue(descriptor)
        }

        context(KtAnalysisSession)
        fun createFromQualified(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            var selector = expr
            while (selector is KtQualifiedExpression) {
                selector = selector.selectorExpression
            }
            return createFromSimpleName(factory, selector)
        }

        context(KtAnalysisSession)
        internal fun KtVariableLikeSymbol.variableDescriptor(): KtVariableDescriptor {
            return KtVariableDescriptor(useSiteModule, this.createPointer(), this.returnType.toDfType(), this.name.hashCode())
        }

        private fun getVariablesChangedInNestedFunctions(parent: KtElement): Set<KtVariableDescriptor> =
            CachedValuesManager.getProjectPsiDependentCache(parent) { scope ->
                val result = hashSetOf<KtVariableDescriptor>()
                analyze(scope) {
                    PsiTreeUtil.processElements(scope) { e ->
                        if (e !is KtSimpleNameExpression || !e.readWriteAccess(false).isWrite) return@processElements true
                        val target = e.mainReference.resolve()
                        if (target !is KtProperty || !target.isLocal ||
                            !PsiTreeUtil.isAncestor(scope, target, true)
                        ) return@processElements true
                        var parentScope: KtFunction?
                        var context = e
                        while (true) {
                            parentScope = PsiTreeUtil.getParentOfType(context, KtFunction::class.java)
                            val maybeLambda = parentScope?.parent as? KtLambdaExpression
                            val maybeCall = (maybeLambda?.parent as? KtLambdaArgument)?.parent as? KtCallExpression
                            if (maybeCall != null && getInlineableLambda(maybeCall)?.lambda == maybeLambda) {
                                context = maybeCall
                                continue
                            }
                            break
                        }
                        if (parentScope != null && PsiTreeUtil.isAncestor(scope, parentScope, true)) {
                            result.add(target.getVariableSymbol().variableDescriptor())
                        }
                        return@processElements true
                    }
                }
                return@getProjectPsiDependentCache result
            }

        context(KtAnalysisSession)
        fun createFromSimpleName(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            val varFactory = factory.varFactory
            if (expr !is KtSimpleNameExpression) return null
            val symbol: KtVariableLikeSymbol = expr.mainReference.resolveToSymbol() as? KtVariableLikeSymbol ?: return null
            if (symbol is KtValueParameterSymbol || symbol is KtLocalVariableSymbol) {
                return varFactory.createVariableValue(symbol.variableDescriptor())
            }
            if (!isTrackableProperty(symbol)) return null
            val parent = expr.parent
            var qualifier: DfaVariableValue? = null
            if ((symbol.getContainingSymbol() as? KtClassOrObjectSymbol)?.classKind == KtClassKind.OBJECT) {
                // property in an object: singleton, can track
                return varFactory.createVariableValue(symbol.variableDescriptor(), null)
            }
            if (parent is KtQualifiedExpression && parent.selectorExpression == expr) {
                val receiver = parent.receiverExpression
                qualifier = createFromSimpleName(factory, receiver)
            } else {
                if (symbol.psi?.parent is KtFile) {
                    // top-level declaration
                    return varFactory.createVariableValue(symbol.variableDescriptor(), null)
                }
                val receiverParameter = (expr.resolveCall()?.singleVariableAccessCall()
                    ?.partiallyAppliedSymbol?.dispatchReceiver as? KtImplicitReceiverValue)?.symbol
                        as? KtReceiverParameterSymbol
                val functionLiteral = receiverParameter?.psi as? KtFunctionLiteral
                val type = receiverParameter?.type
                if (functionLiteral != null && type != null) {
                    qualifier = varFactory.createVariableValue(KtLambdaThisVariableDescriptor(functionLiteral, type.toDfType()))
                } else {
                    val classOrObject = symbol.getContainingSymbol() as? KtClassOrObjectSymbol
                    if (classOrObject != null) {
                        val dfType = TypeConstraints.exactClass(classOrObject.classDef()).instanceOf().asDfType()
                        qualifier = varFactory.createVariableValue(KtThisDescriptor(dfType))
                    }
                }
            }
            if (qualifier != null) {
                return varFactory.createVariableValue(symbol.variableDescriptor(), qualifier)
            }
            return null
        }

        private fun isTrackableProperty(target: KtVariableLikeSymbol?) =
            target is KtPropertySymbol && target.getter?.isDefault != false && target.setter?.isDefault != false
                    && !target.isDelegatedProperty && target.modality == Modality.FINAL
                    && !target.isExtension && target.backingFieldSymbol?.hasAnnotation(JvmStandardClassIds.VOLATILE_ANNOTATION_CLASS_ID) == false
    }
}

class KtLambdaThisVariableDescriptor(val lambda: KtFunctionLiteral, val type: DfType) : JvmVariableDescriptor() {
    override fun getDfType(qualifier: DfaVariableValue?): DfType = type
    override fun isStable(): Boolean = true
    override fun equals(other: Any?): Boolean = other is KtLambdaThisVariableDescriptor && other.lambda == lambda
    override fun hashCode(): Int = lambda.hashCode()
    override fun toString(): String = "this@${lambda.name}"
}
