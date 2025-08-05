// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa

import com.intellij.codeInspection.dataFlow.jvm.descriptors.JvmVariableDescriptor
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType
import com.intellij.codeInspection.dataFlow.types.DfType
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.containingDeclaration
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.functionType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KaSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.useSiteModule
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
    private val inline: Boolean,

    /**
     * This anchor psi element helps to avoid expensive [KaSymbolPointer.pointsToTheSameSymbolAs]
     * comparison until KT-74121 is fixed
     */
    private val sourceAnchorPsi: PsiElement?,
) : JvmVariableDescriptor(), KtBaseDescriptor {
    @OptIn(KaExperimentalApi::class)
    val stable: Boolean by lazy {
        when (val result = analyze(module) {
            when (val symbol = pointer.restoreSymbol()) {
                is KaValueParameterSymbol, is KaContextParameterSymbol, is KaEnumEntrySymbol -> return@analyze true
                is KaPropertySymbol, is KaJavaFieldSymbol -> return@analyze symbol.isVal
                is KaLocalVariableSymbol -> {
                    if (symbol.isVal) return@analyze true
                    val psiElement = when (val declaration = symbol.psi) {
                        is KtProperty -> declaration.parent
                        is KtDestructuringDeclarationEntry -> (declaration.parent as? KtDestructuringDeclaration)?.parent
                        else -> null
                    } as? KtElement
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

    override fun createValue(
        factory: DfaValueFactory,
        qualifier: DfaValue?
    ): DfaValue {
        if (qualifier is DfaVariableValue) {
            if (qualifier.dfType is DfPrimitiveType) return factory.unknown
            return factory.varFactory.createVariableValue(this, qualifier)
        }
        return factory.unknown
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

    override fun equals(other: Any?): Boolean = when {
        other === this -> true
        other !is KtVariableDescriptor -> false
        sourceAnchorPsi != null || other.sourceAnchorPsi != null -> other.sourceAnchorPsi == sourceAnchorPsi
        else -> other.hash == hash && other.pointer.pointsToTheSameSymbolAs(pointer)
    }

    override fun hashCode(): Int = hash

    override fun toString(): String = analyze(module) {
        val symbol = pointer.restoreSymbol() ?: return@analyze "<unknown>"
        symbol.name.asString()
    }

    companion object {
        context(_: KaSession)
        fun getSingleLambdaParameter(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val parameterSymbol = lambda.functionLiteral.symbol.valueParameters.singleOrNull() ?: return null
            if ((parameterSymbol.psi as? KtParameter)?.destructuringDeclaration != null) return null
            return factory.varFactory.createVariableValue(parameterSymbol.variableDescriptor())
        }

        context(_: KaSession)
        fun getLambdaReceiver(factory: DfaValueFactory, lambda: KtLambdaExpression): DfaVariableValue? {
            val receiverType = (lambda.functionLiteral.functionType as? KaFunctionType)?.receiverType ?: return null
            val descriptor = KtLambdaThisVariableDescriptor(lambda.functionLiteral, receiverType.toDfType())
            return factory.varFactory.createVariableValue(descriptor)
        }

        context(_: KaSession)
        fun createFromQualified(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            var selector = expr
            while (selector is KtQualifiedExpression) {
                selector = selector.selectorExpression
            }
            return createFromSimpleName(factory, selector)
        }

        context(_: KaSession)
        internal fun KaVariableSymbol.variableDescriptor(): KtVariableDescriptor {
            val type = this.returnType
            return KtVariableDescriptor(
                module = useSiteModule,
                pointer = this.createPointer(),
                type = type.toDfType(),
                hash = callableId?.hashCode() ?: name.hashCode(),
                inline = ((type as? KaClassType)?.symbol as? KaNamedClassSymbol)?.isInline == true,
                sourceAnchorPsi = if (origin == KaSymbolOrigin.SOURCE) psi else null,
            )
        }

        private fun getVariablesChangedInNestedFunctions(parent: KtElement): Set<KtVariableDescriptor> =
            CachedValuesManager.getProjectPsiDependentCache(parent) { scope ->
                val result = hashSetOf<KtVariableDescriptor>()
                analyze(scope) {
                    PsiTreeUtil.processElements(scope) { e ->
                        if (e !is KtSimpleNameExpression || !e.readWriteAccess(false).isWrite) return@processElements true
                        val target = e.mainReference.resolve()
                        if (!(target is KtProperty && target.isLocal || target is KtDestructuringDeclarationEntry) ||
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
                            result.add((target.symbol as KaVariableSymbol).variableDescriptor())
                        }
                        return@processElements true
                    }
                }
                return@getProjectPsiDependentCache result
            }

        @OptIn(KaExperimentalApi::class)
        context(_: KaSession)
        fun createFromSimpleName(factory: DfaValueFactory, expr: KtExpression?): DfaVariableValue? {
            val varFactory = factory.varFactory
            if (expr is KtThisExpression) {
                return KtThisDescriptor.descriptorFromThis(expr).first?.let { varFactory.createVariableValue(it) }
            }
            if (expr !is KtSimpleNameExpression) return null
            val symbol: KaVariableSymbol = expr.mainReference.resolveToSymbol() as? KaVariableSymbol ?: return null
            if (symbol is KaValueParameterSymbol || symbol is KaLocalVariableSymbol || symbol is KaContextParameterSymbol) {
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

        context(_: KaSession)
        private fun findQualifier(factory: DfaValueFactory, expr: KtSimpleNameExpression, symbol: KaVariableSymbol): DfaVariableValue? {
            val parent = expr.parent
            val varFactory = factory.varFactory
            if (parent is KtQualifiedExpression && parent.selectorExpression == expr) {
                val receiver = parent.receiverExpression
                return createFromSimpleName(factory, receiver)
            }
            var dispatchReceiver = expr.resolveToCall()?.singleVariableAccessCall()?.partiallyAppliedSymbol?.dispatchReceiver
            dispatchReceiver = (dispatchReceiver as? KaSmartCastedReceiverValue)?.original ?: dispatchReceiver
            val receiverParameter = (dispatchReceiver as? KaImplicitReceiverValue)?.symbol as? KaReceiverParameterSymbol
            val functionLiteral = receiverParameter?.psi as? KtFunctionLiteral
            val type = receiverParameter?.returnType
            if (functionLiteral != null && type != null) {
                return varFactory.createVariableValue(KtLambdaThisVariableDescriptor(functionLiteral, type.toDfType()))
            }
            val receiverType = (receiverParameter?.psi as? KtTypeReference)?.type
            if (receiverType != null) {
                return varFactory.createVariableValue(
                    KtThisDescriptor(
                        receiverType.toDfType(),
                        receiverType.expandedSymbol?.classDef(),
                        (expr.parentOfType<KtFunction>() as? KtNamedFunction)?.name
                    )
                )
            }
            val classSymbol = symbol.containingDeclaration as? KaClassSymbol
            if (classSymbol != null) {
                return varFactory.createVariableValue(
                    KtThisDescriptor(classSymbol.classDef(), (expr.parentOfType<KtFunction>() as? KtNamedFunction)?.name)
                )
            }
            return null
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
    override fun equals(other: Any?): Boolean = other === this || other is KtLambdaThisVariableDescriptor && other.lambda == lambda
    override fun hashCode(): Int = lambda.hashCode()
    override fun toString(): String = "this@${lambda.name}"
    override fun createValue(factory: DfaValueFactory, qualifier: DfaValue?): DfaValue {
        if (qualifier != null) return factory.unknown
        return factory.varFactory.createVariableValue(this)
    }
}
