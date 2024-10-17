// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KtClassDef.Companion.classDef
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.*

class KtVariableDescriptor(
    val module: KaModule,
    val pointer: KaSymbolPointer<KaVariableSymbol>,
    val type: DfType,
    val hash: Int,
    private val inline: Boolean
) : JvmVariableDescriptor(), KtBaseDescriptor {
    val stable: Boolean by lazy {
        when (val result = analyze(module) {
            when (val symbol = pointer.restoreSymbol()) {
                is KaValueParameterSymbol, is KaEnumEntrySymbol -> return@analyze true
                is KaPropertySymbol, is KaJavaFieldSymbol -> return@analyze symbol.isVal
                is KaLocalVariableSymbol -> {
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

    override fun isInlineClassReference(): Boolean = inline

    override fun isStable(): Boolean = stable

    override fun canBeCapturedInClosure(): Boolean = analyze(module) {
        val symbol = pointer.restoreSymbol() ?: return@analyze false
        when (symbol) {
            is KaPropertySymbol, is KaJavaFieldSymbol, is KaLocalVariableSymbol -> symbol.isVal
            else -> false
        }
    }

    override fun getDfType(qualifier: DfaVariableValue?): DfType = type

    override fun equals(other: Any?): Boolean = other is KtVariableDescriptor && other.pointer.pointsToTheSameSymbolAs(pointer)

    override fun hashCode(): Int = hash

    override fun toString(): String = analyze(module) {
        val symbol = pointer.restoreSymbol() ?: return@analyze "<unknown>"
        symbol.name.asString()
    }

    companion object {
        context(KaSession)
        fun getSingleLambdaParameter(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val parameterSymbol = lambda.functionLiteral.symbol.valueParameters.singleOrNull() ?: return null
            if ((parameterSymbol.psi as? KtParameter)?.destructuringDeclaration != null) return null
            return factory.varFactory.createVariableValue(parameterSymbol.variableDescriptor())
        }

        context(KaSession)
        fun getLambdaReceiver(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val receiverType = (lambda.functionLiteral.functionType as? KaFunctionType)?.receiverType ?: return null
            val descriptor = KtLambdaThisVariableDescriptor(lambda.functionLiteral, receiverType.toDfType())
            return factory.varFactory.createVariableValue(descriptor)
        }

        context(KaSession)
        fun createFromQualified(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            var selector = expr
            while (selector is KtQualifiedExpression) {
                selector = selector.selectorExpression
            }
            return createFromSimpleName(factory, selector)
        }

        context(KaSession)
        internal fun KaVariableSymbol.variableDescriptor(): KtVariableDescriptor {
            val type = this.returnType
            return KtVariableDescriptor(useSiteModule, this.createPointer(), type.toDfType(), this.name.hashCode(),
                                        ((type as? KaClassType)?.symbol as? KaNamedClassSymbol)?.isInline == true)
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
                            result.add(target.symbol.variableDescriptor())
                        }
                        return@processElements true
                    }
                }
                return@getProjectPsiDependentCache result
            }

        context(KaSession)
        fun createFromSimpleName(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            val varFactory = factory.varFactory
            if (expr is KtThisExpression) {
                return KtThisDescriptor.descriptorFromThis(expr).first?.let { varFactory.createVariableValue(it) }
            }
            if (expr !is KtSimpleNameExpression) return null
            val symbol: KaVariableSymbol = expr.mainReference.resolveToSymbol() as? KaVariableSymbol ?: return null
            if (symbol is KaValueParameterSymbol || symbol is KaLocalVariableSymbol) {
                return varFactory.createVariableValue(symbol.variableDescriptor())
            }
            val qualifier = findQualifier(factory, expr, symbol)
            val specialField = symbol.toSpecialField()
            if (specialField != null) {
                return varFactory.createVariableValue(specialField, qualifier)
            }
            if (!isTrackableProperty(symbol)) return null
            if ((symbol.containingDeclaration as? KaClassSymbol)?.classKind == KaClassKind.OBJECT) {
                // property in an object: singleton, can track
                return varFactory.createVariableValue(symbol.variableDescriptor(), null)
            }
            if (symbol is KaJavaFieldSymbol && symbol.isStatic) {
                // Java static field, can track
                return varFactory.createVariableValue(symbol.variableDescriptor(), null)
            }
            if (symbol.psi?.parent is KtFile) {
                // top-level declaration
                return varFactory.createVariableValue(symbol.variableDescriptor(), null)
            }
            if (qualifier != null) {
                return varFactory.createVariableValue(symbol.variableDescriptor(), qualifier)
            }
            return null
        }

        context(KaSession)
        private fun findQualifier(factory: DfaValueFactory, expr: KtSimpleNameExpression, symbol: KaVariableSymbol): DfaVariableValue? {
            val parent = expr.parent
            var qualifier: DfaVariableValue? = null
            val varFactory = factory.varFactory
            if (parent is KtQualifiedExpression && parent.selectorExpression == expr) {
                val receiver = parent.receiverExpression
                qualifier = createFromSimpleName(factory, receiver)
            } else {
                val receiverParameter = (expr.resolveToCall()?.singleVariableAccessCall()
                    ?.partiallyAppliedSymbol?.dispatchReceiver as? KaImplicitReceiverValue)?.symbol
                        as? KaReceiverParameterSymbol
                val functionLiteral = receiverParameter?.psi as? KtFunctionLiteral
                val type = receiverParameter?.returnType
                if (functionLiteral != null && type != null) {
                    qualifier = varFactory.createVariableValue(KtLambdaThisVariableDescriptor(functionLiteral, type.toDfType()))
                } else {
                    val classSymbol = (((receiverParameter?.psi as? KtTypeReference)?.type as? KaClassType)?.symbol as? KaClassSymbol)
                                ?: symbol.containingDeclaration as? KaClassSymbol
                    if (classSymbol != null) {
                        qualifier = varFactory.createVariableValue(
                            KtThisDescriptor(classSymbol.classDef(), (expr.parentOfType<KtFunction>() as? KtNamedFunction)?.name))
                    }
                }
            }
            return qualifier
        }

        private fun isTrackableProperty(target: KaVariableSymbol?): Boolean {
            return isJavaField(target) || isKotlinProperty(target)
        }

        private fun isKotlinProperty(target: KaVariableSymbol?) =
            target is KaPropertySymbol && target.getter?.isDefault != false && target.setter?.isDefault != false
                    && !target.isDelegatedProperty && target.modality == KaSymbolModality.FINAL
                    && !target.isExtension
                    && target.backingFieldSymbol?.annotations?.contains(JvmStandardClassIds.VOLATILE_ANNOTATION_CLASS_ID) == false

        private fun isJavaField(target: KaVariableSymbol?) =
            target is KaJavaFieldSymbol && (target.psi as? PsiField)?.hasModifierProperty(PsiModifier.VOLATILE) == false
    }
}

class KtLambdaThisVariableDescriptor(val lambda: KtFunctionLiteral, val type: DfType) : JvmVariableDescriptor() {
    override fun getDfType(qualifier: DfaVariableValue?): DfType = type
    override fun isStable(): Boolean = true
    override fun equals(other: Any?): Boolean = other is KtLambdaThisVariableDescriptor && other.lambda == lambda
    override fun hashCode(): Int = lambda.hashCode()
    override fun toString(): String = "this@${lambda.name}"
}
