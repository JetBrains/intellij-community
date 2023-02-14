// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.psi.PsiReference
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.core.setDefaultValue
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.KotlinCallableDefinitionUsage
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.isPrimaryConstructorOfDataClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.addRemoveModifier.setModifierList
import org.jetbrains.kotlin.psi.psiUtil.quoteIfNeeded
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.descriptorUtil.isAnnotationConstructor
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.AbstractTypeChecker
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.checker.ClassicTypeSystemContext
import org.jetbrains.kotlin.types.checker.createClassicTypeCheckerState
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

class KotlinParameterInfo(
    val callableDescriptor: CallableDescriptor,
    val originalIndex: Int = -1,
    private var name: String,
    val originalTypeInfo: KotlinTypeInfo = KotlinTypeInfo(false),
    var defaultValueForCall: KtExpression? = null,
    var defaultValueAsDefaultParameter: Boolean = false,
    var valOrVar: KotlinValVar = defaultValOrVar(callableDescriptor),
    val modifierList: KtModifierList? = null,
) : ParameterInfo {
    private var _defaultValueForParameter: KtExpression? = null
    fun setDefaultValueForParameter(expression: KtExpression?) {
        _defaultValueForParameter = expression
    }

    val defaultValue: KtExpression? get() = defaultValueForCall?.takeIf { defaultValueAsDefaultParameter } ?: _defaultValueForParameter

    var currentTypeInfo: KotlinTypeInfo = originalTypeInfo

    val defaultValueParameterReferences: Map<PsiReference, DeclarationDescriptor> by lazy {
        collectDefaultValueParameterReferences(defaultValueForCall)
    }

    private fun collectDefaultValueParameterReferences(defaultValueForCall: KtExpression?): Map<PsiReference, DeclarationDescriptor> {
        val file = defaultValueForCall?.containingFile as? KtFile ?: return emptyMap()
        if (!file.isPhysical && file.analysisContext == null) return emptyMap()
        return CollectParameterRefsVisitor(callableDescriptor, defaultValueForCall).run()
    }

    override fun getOldIndex(): Int = originalIndex

    val isNewParameter: Boolean
        get() = originalIndex == -1

    override fun getDefaultValue(): String? = null

    override fun getName(): String = name

    override fun setName(name: String?) {
        this.name = name ?: ""
    }

    override fun getTypeText(): String = currentTypeInfo.render()

    val isTypeChanged: Boolean get() = !currentTypeInfo.isEquivalentTo(originalTypeInfo)

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {
        throw UnsupportedOperationException()
    }

    fun renderType(parameterIndex: Int, inheritedCallable: KotlinCallableDefinitionUsage<*>): String {
        val defaultRendering = currentTypeInfo.render()
        val typeSubstitutor = inheritedCallable.typeSubstitutor ?: return defaultRendering
        val currentBaseFunction = inheritedCallable.baseFunction.currentCallableDescriptor ?: return defaultRendering
        val parameter = currentBaseFunction.valueParameters[parameterIndex]
        if (parameter.isVararg) return defaultRendering
        val parameterType = parameter.type
        if (parameterType.isError) return defaultRendering

        val originalType = inheritedCallable.originalCallableDescriptor.valueParameters.getOrNull(originalIndex)?.type
        val typeToRender = originalType?.takeIf {
            val checker = OverridingTypeCheckerContext.createChecker(inheritedCallable.originalCallableDescriptor, currentBaseFunction)
            AbstractTypeChecker.equalTypes(checker, originalType.unwrap(), parameterType.unwrap())
        } ?: parameterType

        return typeToRender.renderTypeWithSubstitution(typeSubstitutor, defaultRendering, true)
    }

    fun getInheritedName(inheritedCallable: KotlinCallableDefinitionUsage<*>): String {
        val name = this.name.quoteIfNeeded()
        if (!inheritedCallable.isInherited) return name

        val baseFunction = inheritedCallable.baseFunction
        val baseFunctionDescriptor = baseFunction.originalCallableDescriptor

        val inheritedFunctionDescriptor = inheritedCallable.originalCallableDescriptor
        val inheritedParameterDescriptors = inheritedFunctionDescriptor.valueParameters
        if (originalIndex < 0
            || originalIndex >= baseFunctionDescriptor.valueParameters.size
            || originalIndex >= inheritedParameterDescriptors.size
        ) return name

        val inheritedParamName = inheritedParameterDescriptors[originalIndex].name.render()
        val oldParamName = baseFunctionDescriptor.valueParameters[originalIndex].name.render()

        return when {
            oldParamName == inheritedParamName && inheritedFunctionDescriptor !is AnonymousFunctionDescriptor -> name
            else -> inheritedParamName
        }
    }

    fun requiresExplicitType(inheritedCallable: KotlinCallableDefinitionUsage<*>): Boolean {
        val inheritedFunctionDescriptor = inheritedCallable.originalCallableDescriptor
        if (inheritedFunctionDescriptor !is AnonymousFunctionDescriptor) return true

        if (originalIndex < 0) return !inheritedCallable.hasExpectedType

        val inheritedParameterDescriptor = inheritedFunctionDescriptor.valueParameters[originalIndex]
        val parameter = DescriptorToSourceUtils.descriptorToDeclaration(inheritedParameterDescriptor) as? KtParameter ?: return false
        return parameter.typeReference != null
    }

    private fun getOriginalParameter(inheritedCallable: KotlinCallableDefinitionUsage<*>): KtParameter? {
        return (inheritedCallable.declaration as? KtFunction)?.valueParameters?.getOrNull(originalIndex)
    }

    private fun buildNewParameter(inheritedCallable: KotlinCallableDefinitionUsage<*>, parameterIndex: Int): KtParameter {
        val psiFactory = KtPsiFactory(inheritedCallable.project)

        val buffer = StringBuilder()

        if (modifierList != null) {
            buffer.append(modifierList.text).append(' ')
        }

        if (valOrVar != KotlinValVar.None) {
            buffer.append(valOrVar).append(' ')
        }

        buffer.append(getInheritedName(inheritedCallable))

        if (requiresExplicitType(inheritedCallable)) {
            buffer.append(": ").append(renderType(parameterIndex, inheritedCallable))
        }

        if (!inheritedCallable.isInherited) {
            defaultValue?.let { buffer.append(" = ").append(it.text) }
        }

        return psiFactory.createParameter(buffer.toString())
    }

    fun getDeclarationSignature(parameterIndex: Int, inheritedCallable: KotlinCallableDefinitionUsage<*>): KtParameter {
        val originalParameter = getOriginalParameter(inheritedCallable)
            ?: return buildNewParameter(inheritedCallable, parameterIndex)

        val psiFactory = KtPsiFactory(originalParameter.project)
        val newParameter = originalParameter.copied()

        modifierList?.let { newParameter.setModifierList(it) }

        if (valOrVar != newParameter.valOrVarKeyword.toValVar()) {
            newParameter.setValOrVar(valOrVar)
        }

        val newName = getInheritedName(inheritedCallable)
        if (newParameter.name != newName) {
            newParameter.setName(newName.quoteIfNeeded())
        }

        if (newParameter.typeReference != null || requiresExplicitType(inheritedCallable)) {
            newParameter.typeReference = psiFactory.createType(renderType(parameterIndex, inheritedCallable))
        }

        if (!inheritedCallable.isInherited) {
            defaultValue?.let { newParameter.setDefaultValue(it) }
        }

        return newParameter
    }

    private class CollectParameterRefsVisitor(
        private val callableDescriptor: CallableDescriptor,
        private val expressionToProcess: KtExpression
    ) : KtTreeVisitorVoid() {
        private val refs = LinkedHashMap<PsiReference, DeclarationDescriptor>()
        private val bindingContext = expressionToProcess.analyze(BodyResolveMode.PARTIAL)
        private val project = expressionToProcess.project

        fun run(): Map<PsiReference, DeclarationDescriptor> {
            expressionToProcess.accept(this)
            return refs
        }

        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            val ref = expression.mainReference
            val descriptor = targetToCollect(expression, ref) ?: return
            refs[ref] = descriptor
        }

        private fun targetToCollect(expression: KtSimpleNameExpression, ref: KtReference): DeclarationDescriptor? {
            val descriptor = ref.resolveToDescriptors(bindingContext).singleOrNull()
            if (descriptor is ValueParameterDescriptor) {
                return takeIfOurParameter(descriptor)
            }

            if (descriptor is PropertyDescriptor && callableDescriptor is ConstructorDescriptor) {
                val parameter = DescriptorToSourceUtils.getSourceFromDescriptor(descriptor)
                if (parameter is KtParameter) {
                    return takeIfOurParameter(bindingContext[BindingContext.VALUE_PARAMETER, parameter])
                }
            }

            val resolvedCall = expression.getResolvedCall(bindingContext) ?: return null
            (resolvedCall.resultingDescriptor as? ReceiverParameterDescriptor)?.let {
                return if (takeIfOurReceiver(it.containingDeclaration) != null) it else null
            }

            takeIfOurReceiver(resolvedCall.extensionReceiver as? ImplicitReceiver)?.let { return it }
            takeIfOurReceiver(resolvedCall.dispatchReceiver as? ImplicitReceiver)?.let { return it }

            return null
        }

        private fun compareDescriptors(currentDescriptor: DeclarationDescriptor?, originalDescriptor: DeclarationDescriptor?): Boolean {
            return compareDescriptors(project, currentDescriptor, originalDescriptor)
        }

        private fun takeIfOurParameter(parameter: DeclarationDescriptor?): ValueParameterDescriptor? {
            return (parameter as? ValueParameterDescriptor)
                ?.takeIf { compareDescriptors(it.containingDeclaration, callableDescriptor) }
        }

        private fun takeIfOurReceiver(receiverDescriptor: DeclarationDescriptor?): DeclarationDescriptor? {
            return receiverDescriptor?.takeIf {
                compareDescriptors(it, callableDescriptor.extensionReceiverParameter?.containingDeclaration)
                        || compareDescriptors(it, callableDescriptor.dispatchReceiverParameter?.containingDeclaration)
            }
        }

        private fun takeIfOurReceiver(receiver: ImplicitReceiver?): DeclarationDescriptor? {
            return takeIfOurReceiver(receiver?.declarationDescriptor)
        }
    }
}

private fun defaultValOrVar(callableDescriptor: CallableDescriptor): KotlinValVar =
    if (callableDescriptor.isAnnotationConstructor() || callableDescriptor.isPrimaryConstructorOfDataClass)
        KotlinValVar.Val
    else
        KotlinValVar.None

private class OverridingTypeCheckerContext(private val matchingTypeConstructors: Map<TypeConstructorMarker, TypeConstructor>) :
    ClassicTypeSystemContext {

    override fun areEqualTypeConstructors(c1: TypeConstructorMarker, c2: TypeConstructorMarker): Boolean {
        return super.areEqualTypeConstructors(c1, c2) || run {
            val img1 = matchingTypeConstructors[c1]
            val img2 = matchingTypeConstructors[c2]
            img1 != null && img1 == c2 || img2 != null && img2 == c1
        }
    }

    companion object {
        fun createChecker(superDescriptor: CallableDescriptor, subDescriptor: CallableDescriptor): TypeCheckerState {
            val context = OverridingTypeCheckerContext(subDescriptor.typeParameters.zip(superDescriptor.typeParameters).associate {
                it.first.typeConstructor to it.second.typeConstructor
            })

            return createClassicTypeCheckerState(isErrorTypeEqualsToAnything = false, typeSystemContext = context)
        }
    }
}
