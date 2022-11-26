// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature.usages

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.impl.AnonymousFunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMethodDescriptor
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.ShortenReferences.Options
import org.jetbrains.kotlin.idea.core.setVisibility
import org.jetbrains.kotlin.idea.core.toKeywordToken
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.getCallableSubstitutor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.setValOrVar
import org.jetbrains.kotlin.idea.refactoring.dropOperatorKeywordIfNecessary
import org.jetbrains.kotlin.idea.refactoring.dropOverrideKeywordIfNecessary
import org.jetbrains.kotlin.idea.refactoring.replaceListPsiAndKeepDelimiters
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.toSubstitutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getValueParameterList
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.kotlin.utils.sure

class KotlinCallableDefinitionUsage<T : PsiElement>(
    function: T,
    val originalCallableDescriptor: CallableDescriptor,
    baseFunction: KotlinCallableDefinitionUsage<PsiElement>?,
    private val samCallType: KotlinType?,
    private val canDropOverride: Boolean = true
) : KotlinUsageInfo<T>(function) {
    val baseFunction: KotlinCallableDefinitionUsage<*> = baseFunction ?: this

    val hasExpectedType: Boolean = checkIfHasExpectedType(originalCallableDescriptor, isInherited)

    val currentCallableDescriptor: CallableDescriptor? by lazy {
        when (val element = declaration) {
            is KtFunction, is KtProperty, is KtParameter -> (element as KtDeclaration).unsafeResolveToDescriptor() as CallableDescriptor
            is KtClass -> (element.unsafeResolveToDescriptor() as ClassDescriptor).unsubstitutedPrimaryConstructor
            is PsiMethod -> element.getJavaMethodDescriptor()
            else -> null
        }
    }

    val typeSubstitutor: TypeSubstitutor? by lazy {
        if (!isInherited) return@lazy null

        if (samCallType == null) {
            getCallableSubstitutor(this.baseFunction, this)
        } else {
            val currentBaseDescriptor = this.baseFunction.currentCallableDescriptor
            val classDescriptor = currentBaseDescriptor?.containingDeclaration as? ClassDescriptor ?: return@lazy null
            getTypeSubstitution(classDescriptor.defaultType, samCallType)?.toSubstitutor()
        }
    }

    private fun checkIfHasExpectedType(callableDescriptor: CallableDescriptor, isInherited: Boolean): Boolean {
        if (!(callableDescriptor is AnonymousFunctionDescriptor && isInherited)) return false

        val functionLiteral = DescriptorToSourceUtils.descriptorToDeclaration(callableDescriptor) as KtFunctionLiteral?
        assert(functionLiteral != null) { "No declaration found for $callableDescriptor" }

        val parent = functionLiteral!!.parent as? KtLambdaExpression ?: return false

        return parent.analyze(BodyResolveMode.PARTIAL)[BindingContext.EXPECTED_EXPRESSION_TYPE, parent] != null
    }

    val declaration: PsiElement
        get() = element!!

    val isInherited: Boolean
        get() = baseFunction !== this

    override fun processUsage(changeInfo: KotlinChangeInfo, element: T, allUsages: Array<out UsageInfo>): Boolean {
        if (element !is KtNamedDeclaration) return true

        val psiFactory = KtPsiFactory(element.project)

        if (changeInfo.isNameChanged) {
            val identifier = (element as KtCallableDeclaration).nameIdentifier

            identifier?.replace(psiFactory.createIdentifier(changeInfo.newName))
        }

        changeReturnTypeIfNeeded(changeInfo, element)

        val parameterList = element.getValueParameterList()

        if (changeInfo.isParameterSetOrOrderChanged) {
            processParameterListWithStructuralChanges(changeInfo, element, parameterList, psiFactory)
        } else if (parameterList != null) {
            val offset = if (originalCallableDescriptor.extensionReceiverParameter != null) 1 else 0
            for ((paramIndex, parameter) in parameterList.parameters.withIndex()) {
                val parameterInfo = changeInfo.newParameters[paramIndex + offset]
                changeParameter(paramIndex, parameter, parameterInfo)
            }

            parameterList.addToShorteningWaitSet(Options.DEFAULT)
        }

        if (element is KtCallableDeclaration && changeInfo.isReceiverTypeChanged()) {
            val receiverTypeText = changeInfo.renderReceiverType(this)
            val receiverTypeRef = if (receiverTypeText != null) psiFactory.createType(receiverTypeText) else null
            val newReceiverTypeRef = element.setReceiverTypeReference(receiverTypeRef)
            newReceiverTypeRef?.addToShorteningWaitSet(Options.DEFAULT)
        }

        if (changeInfo.isVisibilityChanged() && !KtPsiUtil.isLocal(element as KtDeclaration)) {
            changeVisibility(changeInfo, element)
        }

        if (canDropOverride) {
            dropOverrideKeywordIfNecessary(element)
        }
        dropOperatorKeywordIfNecessary(element)

        return true
    }

    private fun changeReturnTypeIfNeeded(changeInfo: KotlinChangeInfo, element: PsiElement) {
        if (element !is KtCallableDeclaration) return
        if (element is KtConstructor<*>) return

        val returnTypeIsNeeded = (element is KtFunction && element !is KtFunctionLiteral) || element is KtProperty || element is KtParameter

        if (changeInfo.isReturnTypeChanged && returnTypeIsNeeded) {
            element.typeReference = null
            val returnTypeText = changeInfo.renderReturnType(this)
            val returnType = changeInfo.newReturnTypeInfo.type
            if (returnType == null || !returnType.isUnit()) {
                element.setTypeReference(KtPsiFactory(element.project).createType(returnTypeText))!!.addToShorteningWaitSet(Options.DEFAULT)
            }
        }
    }

    private fun processParameterListWithStructuralChanges(
        changeInfo: KotlinChangeInfo,
        element: PsiElement,
        originalParameterList: KtParameterList?,
        psiFactory: KtPsiFactory
    ) {
        var parameterList = originalParameterList
        val parametersCount = changeInfo.getNonReceiverParametersCount()
        val isLambda = element is KtFunctionLiteral
        var canReplaceEntireList = false

        var newParameterList: KtParameterList? = null
        if (isLambda) {
            if (parametersCount == 0) {
                if (parameterList != null) {
                    parameterList.delete()
                    val arrow = (element as KtFunctionLiteral).arrow
                    arrow?.delete()
                    parameterList = null
                }
            } else {
                newParameterList = psiFactory.createLambdaParameterList(changeInfo.getNewParametersSignatureWithoutParentheses(this))
                canReplaceEntireList = true
            }
        } else if (!(element is KtProperty || element is KtParameter)) {
            newParameterList = psiFactory.createParameterList(changeInfo.getNewParametersSignature(this))
        }

        if (newParameterList == null) return

        if (parameterList != null) {
            newParameterList = if (canReplaceEntireList) {
                parameterList.replace(newParameterList) as KtParameterList
            } else {
                adjustTrailingComments(parameterList, newParameterList, psiFactory)
                replaceListPsiAndKeepDelimiters(changeInfo, parameterList, newParameterList) { parameters }
            }
        } else {
            if (element is KtClass) {
                val constructor = element.createPrimaryConstructorIfAbsent()
                val oldParameterList = constructor.valueParameterList.sure { "primary constructor from factory has parameter list" }
                newParameterList = oldParameterList.replace(newParameterList) as KtParameterList
            } else if (isLambda) {
                val functionLiteral = element as KtFunctionLiteral
                val anchor = functionLiteral.lBrace
                newParameterList = element.addAfter(newParameterList, anchor) as KtParameterList
                if (functionLiteral.arrow == null) {
                    val whitespaceAndArrow = psiFactory.createWhitespaceAndArrow()
                    element.addRangeAfter(whitespaceAndArrow.first, whitespaceAndArrow.second, newParameterList)
                }
            }
        }

        newParameterList.addToShorteningWaitSet(Options.DEFAULT)
    }

    private fun changeVisibility(changeInfo: KotlinChangeInfo, element: PsiElement) {
        val newVisibilityToken = changeInfo.newVisibility.toKeywordToken()
        when (element) {
            is KtCallableDeclaration -> element.setVisibility(newVisibilityToken)
            is KtClass -> element.createPrimaryConstructorIfAbsent().setVisibility(newVisibilityToken)
            else -> throw AssertionError("Invalid element: " + element.getElementTextWithContext())
        }
    }

    private fun changeParameter(parameterIndex: Int, parameter: KtParameter, parameterInfo: KotlinParameterInfo) {
        parameter.setValOrVar(parameterInfo.valOrVar)

        val psiFactory = KtPsiFactory(project)

        if (parameterInfo.isTypeChanged && parameter.typeReference != null) {
            val renderedType = parameterInfo.renderType(parameterIndex, this)
            parameter.typeReference = psiFactory.createType(renderedType)
        }

        val inheritedName = parameterInfo.getInheritedName(this)
        if (Name.isValidIdentifier(inheritedName)) {
            val newIdentifier = psiFactory.createIdentifier(inheritedName)
            parameter.nameIdentifier?.replace(newIdentifier)
        }
    }

    private fun adjustTrailingComments(oldParameterList: KtParameterList, newParameterList: KtParameterList, psiFactory: KtPsiFactory) {
        val oldLastParameter = oldParameterList.parameters.lastOrNull() ?: return
        val oldLastParameterName = oldLastParameter.name ?: return
        val (firstTrailingComment, lastTrailingComment) =
            oldLastParameter.nextComma().nextCommentsOnSameLine().ifEmpty { return }.let { it.first() to it.last() }

        val newParameter = newParameterList.parameters.firstOrNull { it.name == oldLastParameterName } ?: return
        val newParameterComma = newParameter.nextComma() ?: return
        newParameterList.addRangeAfter(firstTrailingComment, lastTrailingComment, newParameterComma)
        newParameterList.addAfter(psiFactory.createWhiteSpace(), newParameterComma)

        oldParameterList.deleteChildRange(firstTrailingComment, lastTrailingComment)
    }

    private fun KtParameter.nextComma(): PsiElement? =
        getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.elementType == KtTokens.COMMA }

    private fun PsiElement?.nextCommentsOnSameLine(): List<PsiElement> {
        if (this == null) return emptyList()
        val siblings = siblings(withSelf = false)
            .takeWhile { it is PsiComment || (it is PsiWhiteSpace && !it.textContains('\n')) }
            .dropWhile { it is PsiWhiteSpace }
        return if (siblings.any { it is PsiComment }) siblings.toList() else emptyList()
    }
}
