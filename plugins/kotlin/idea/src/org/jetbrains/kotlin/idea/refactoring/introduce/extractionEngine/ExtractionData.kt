// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.unifier.KotlinPsiRange
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.compareDescriptors
import org.jetbrains.kotlin.idea.refactoring.introduce.K1ExtractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.extractableSubstringInfo
import org.jetbrains.kotlin.idea.refactoring.introduce.substringContextOrThis
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getDataFlowInfoAfter
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.tasks.isSynthesizedInvoke
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.hasBothReceivers
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.KotlinType

@K1Deprecation
data class ExtractionData(
    override val originalFile: KtFile,
    override val originalRange: KotlinPsiRange,
    override val targetSibling: PsiElement,
    override val duplicateContainer: PsiElement? = null,
    override val options: ExtractionOptions = ExtractionOptions.DEFAULT
) : IExtractionData {
    override val project: Project = originalFile.project
    override val originalElements: List<PsiElement> = originalRange.elements
    override val physicalElements = originalElements.map { it.substringContextOrThis }

    override val substringInfo: K1ExtractableSubstringInfo?
        get() = (originalElements.singleOrNull() as? KtExpression)?.extractableSubstringInfo as? K1ExtractableSubstringInfo

    override val insertBefore: Boolean = options.extractAsProperty
            || targetSibling.getStrictParentOfType<KtDeclaration>()?.let {
        it is KtDeclarationWithBody || it is KtAnonymousInitializer
    } ?: false

    override val expressions: List<KtExpression> = originalElements.filterIsInstance<KtExpression>()

    override val codeFragmentText: String by lazy {
        val originalElements = originalElements
        when (originalElements.size) {
            0 -> ""
            1 -> originalElements.first().text
            else -> originalFile.text.substring(originalElements.first().startOffset, originalElements.last().endOffset)
        }
    }

    override val commonParent: KtElement = PsiTreeUtil.findCommonParent(physicalElements) as KtElement

    val bindingContext: BindingContext by lazy { commonParent.analyze() }

    private val itFakeDeclaration by lazy { KtPsiFactory(project).createParameter("it: Any?") }
    private val synthesizedInvokeDeclaration by lazy { KtPsiFactory(project).createFunction("fun invoke() {}") }

    init {
        encodeReferences<DeclarationDescriptor, ResolvedCall<*>>(false, { bindingContext[BindingContext.SMARTCAST, it] != null }) { physicalRef ->
            val resolvedCall = physicalRef.getResolvedCall(bindingContext)
            val descriptor =
                bindingContext[BindingContext.REFERENCE_TARGET, physicalRef]
            val declaration = descriptor?.let { getDeclaration(descriptor, bindingContext) }

            declaration?.let { ResolveResult<DeclarationDescriptor, ResolvedCall<*>>(physicalRef, declaration, descriptor, resolvedCall) }
        }
    }

    private fun isExtractableIt(descriptor: DeclarationDescriptor, context: BindingContext): Boolean {
        if (!(descriptor is ValueParameterDescriptor && (context[BindingContext.AUTO_CREATED_IT, descriptor] == true))) return false
        val function = DescriptorToSourceUtils.descriptorToDeclaration(descriptor.containingDeclaration) as? KtFunctionLiteral
        return function == null || !function.isInsideOf(physicalElements)
    }

    private tailrec fun getDeclaration(descriptor: DeclarationDescriptor, context: BindingContext): PsiElement? {
        val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
        if (declaration is PsiNameIdentifierOwner) {
            return declaration
        }

        return when {
            isExtractableIt(descriptor, context) -> itFakeDeclaration
            isSynthesizedInvoke(descriptor) -> synthesizedInvokeDeclaration
            descriptor is SyntheticJavaPropertyDescriptor -> getDeclaration(descriptor.getMethod, context)
            else -> declaration
        }
    }

    private fun getPossibleTypes(expression: KtExpression, resolvedCall: ResolvedCall<*>?, context: BindingContext): Set<KotlinType> {
        val dataFlowValueFactory = expression.getResolutionFacade().dataFlowValueFactory
        val dataFlowInfo = context.getDataFlowInfoAfter(expression)

        resolvedCall?.getImplicitReceiverValue()?.let {
            return dataFlowInfo.getCollectedTypes(
                dataFlowValueFactory.createDataFlowValueForStableReceiver(it),
                expression.languageVersionSettings
            )
        }

        val type = resolvedCall?.resultingDescriptor?.returnType ?: return emptySet()
        val containingDescriptor = expression.getResolutionScope(context, expression.getResolutionFacade()).ownerDescriptor
        val dataFlowValue = dataFlowValueFactory.createDataFlowValue(expression, type, context, containingDescriptor)
        return dataFlowInfo.getCollectedTypes(dataFlowValue, expression.languageVersionSettings)
    }

    fun getBrokenReferencesInfo(body: KtBlockExpression): List<ResolvedReferenceInfo<DeclarationDescriptor, ResolvedCall<*>, KotlinType>> {
        val originalContext = bindingContext

        val newReferences = body.collectDescendantsOfType<KtSimpleNameExpression> { it.resolveResult != null }

        val context = body.analyze()

        val referencesInfo = ArrayList<ResolvedReferenceInfo<DeclarationDescriptor, ResolvedCall<*>, KotlinType>>()
        for (newRef in newReferences) {
            val originalResolveResult = newRef.resolveResult as? ResolveResult<DeclarationDescriptor, ResolvedCall<*>> ?: continue

            val smartCast: KotlinType?
            val possibleTypes: Set<KotlinType>

            // Qualified property reference: a.b
            val qualifiedExpression = newRef.getQualifiedExpressionForSelector()
            if (qualifiedExpression != null) {
                val smartCastTarget = originalResolveResult.originalRefExpr.parent as KtExpression
                smartCast = originalContext[BindingContext.SMARTCAST, smartCastTarget]?.defaultType
                possibleTypes = getPossibleTypes(smartCastTarget, originalResolveResult.resolvedCall, originalContext)
                val receiverDescriptor =
                    (originalResolveResult.resolvedCall?.dispatchReceiver as? ImplicitReceiver)?.declarationDescriptor
                val shouldSkipPrimaryReceiver = smartCast == null
                        && !DescriptorUtils.isCompanionObject(receiverDescriptor)
                        && qualifiedExpression.receiverExpression !is KtSuperExpression
                if (shouldSkipPrimaryReceiver && originalResolveResult.resolvedCall?.hasBothReceivers() != true) continue
            } else {
                if (newRef.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference } != null) continue
                smartCast = originalContext[BindingContext.SMARTCAST, originalResolveResult.originalRefExpr]?.defaultType
                possibleTypes = getPossibleTypes(originalResolveResult.originalRefExpr, originalResolveResult.resolvedCall, originalContext)
            }

            val parent = newRef.parent

            // Skip P in type references like 'P.Q'
            if (parent is KtUserType && (parent.parent as? KtUserType)?.qualifier == parent) continue

            val descriptor = context[BindingContext.REFERENCE_TARGET, newRef]
            val isBadRef = !(compareDescriptors(project, originalResolveResult.descriptor, descriptor)
                    && originalContext.diagnostics.forElement(originalResolveResult.originalRefExpr) == context.diagnostics.forElement(
                newRef
            ))
                    || smartCast != null
            if (isBadRef && !originalResolveResult.declaration.isInsideOf(physicalElements)) {
                val originalResolvedCall = originalResolveResult.resolvedCall as? VariableAsFunctionResolvedCall
                val originalFunctionCall = originalResolvedCall?.functionCall
                val originalVariableCall = originalResolvedCall?.variableCall
                val invokeDescriptor = originalFunctionCall?.resultingDescriptor
                if (invokeDescriptor != null) {
                    val invokeDeclaration = getDeclaration(invokeDescriptor, context) ?: synthesizedInvokeDeclaration
                    val variableResolveResult = originalResolveResult.copy(
                        resolvedCall = originalVariableCall!!,
                        descriptor = originalVariableCall.resultingDescriptor
                    )
                    val functionResolveResult = originalResolveResult.copy(
                        resolvedCall = originalFunctionCall,
                        descriptor = originalFunctionCall.resultingDescriptor,
                        declaration = invokeDeclaration
                    )
                    referencesInfo.add(
                        ResolvedReferenceInfo(
                            newRef,
                            variableResolveResult,
                            smartCast,
                            possibleTypes,
                        )
                    )
                    referencesInfo.add(
                        ResolvedReferenceInfo(
                            newRef,
                            functionResolveResult,
                            smartCast,
                            possibleTypes,
                        )
                    )
                } else {
                    referencesInfo.add(
                        ResolvedReferenceInfo(
                            newRef,
                            originalResolveResult,
                            smartCast,
                            possibleTypes,
                        )
                    )
                }
            }
        }

        return referencesInfo
    }

    override fun dispose() {
        expressions.forEach(::unmarkReferencesInside)
    }
}