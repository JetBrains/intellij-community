// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.typeIfSafeToResolve
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.getThisLabelName
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.getThisWithLabel
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference

internal class UnusedReceiverParameterInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            checkElement(function, holder)
        }

        override fun visitProperty(property: KtProperty) {
            checkElement(property, holder)
        }
    }

    private fun registerProblem(
        holder: ProblemsHolder,
        receiverTypeReference: KtTypeReference,
        textForReceiver: String?
    ) {
        holder.registerProblem(
            receiverTypeReference,
            KotlinBundle.message("inspection.unused.receiver.parameter"),
            RemoveReceiverFix(textForReceiver)
        )
    }

    private fun checkElement(callableDeclaration: KtCallableDeclaration, holder: ProblemsHolder) {
        val receiverTypeReference = callableDeclaration.receiverTypeReference
        if (receiverTypeReference == null || receiverTypeReference.textRange.isEmpty) return

        if (callableDeclaration is KtProperty && callableDeclaration.accessors.isEmpty()) return
        if (callableDeclaration is KtNamedFunction) {
            if (!callableDeclaration.hasBody()) return
            if (callableDeclaration.name == null) {
                val parentQualified = callableDeclaration.getStrictParentOfType<KtQualifiedExpression>()
                if (KtPsiUtil.deparenthesize(parentQualified?.callExpression?.calleeExpression) == callableDeclaration) return
            }
        }

        if (callableDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
            callableDeclaration.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
            callableDeclaration.hasModifier(KtTokens.INFIX_KEYWORD) ||
            callableDeclaration.hasActualModifier() ||
            callableDeclaration.isOverridable() ||
            (callableDeclaration is KtProperty && callableDeclaration.delegate != null)
        ) return

        analyze(callableDeclaration) {
            if (callableDeclaration.expectedType != null) return
            val usedTypeParametersInReceiver = callableDeclaration.collectDescendantsOfType<KtTypeReference>()
                .mapNotNull { (it.typeIfSafeToResolve as? KaTypeParameterType)?.symbol }
                .filterTo(mutableSetOf()) { it.isReified }

            val receiverType = receiverTypeReference.typeIfSafeToResolve
            val receiverTypeSymbol = receiverType?.symbol
            if (receiverTypeSymbol is KaClassSymbol && receiverTypeSymbol.classKind == KaClassKind.COMPANION_OBJECT) return

            val callableSymbol = callableDeclaration.symbol

            val containingDeclarationSymbol = callableSymbol.containingDeclaration
            if (containingDeclarationSymbol != null && containingDeclarationSymbol == receiverTypeSymbol) {
                val thisLabelName = containingDeclarationSymbol.getThisLabelName()
                val thisLabelNamesInCallable =
                    callableDeclaration.collectDescendantsOfType<KtThisExpression>().mapNotNull { it.getLabelName() }
                if (thisLabelNamesInCallable.isNotEmpty()) {
                    if (thisLabelNamesInCallable.none { it == thisLabelName }) {
                        registerProblem(holder, receiverTypeReference, callableSymbol.getThisWithLabel())
                    }
                    return
                }
            }

            if (!isReceiverUsedInside(callableDeclaration, usedTypeParametersInReceiver)) {
                registerProblem(holder, receiverTypeReference, textForReceiver = null)
            }
        }
    }

    private class RemoveReceiverFix(private val textForReceiver: String?) : LocalQuickFix {
        override fun getFamilyName(): String =
            KotlinBundle.message("fix.unused.receiver.parameter.remove")

        override fun startInWriteAction(): Boolean = false


        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtTypeReference ?: return
            if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return
            val function = element.parent as? KtCallableDeclaration ?: return

            val typeReference = function.receiverTypeReference ?: return
            val typeParameters = typeParameters(typeReference)

            if (textForReceiver != null) {
                runWriteAction {
                    function.forEachDescendantOfType<KtThisExpression> {
                        if (it.text == textForReceiver) it.labelQualifier?.delete()
                    }
                    function.setReceiverTypeReference(null)
                }
            } else {
                val methodDescriptor = KotlinMethodDescriptor(function)
                val changeInfo = KotlinChangeInfo(methodDescriptor)
                changeInfo.removeParameter(0)
                KotlinChangeSignatureProcessor(project, changeInfo).run()
            }

            removeUnusedTypeParameters(typeParameters)
        }
    }
}

context(KaSession)
fun isReceiverUsedInside(
    callableDeclaration: KtCallableDeclaration,
    usedTypeParametersInReceiver: Set<KaTypeParameterSymbol>
): Boolean {
    val callableSymbol: KaDeclarationSymbol = callableDeclaration.symbol
    var used = false
    callableDeclaration.acceptChildren(object : KtVisitorVoid(), PsiRecursiveVisitor {
        override fun visitKtElement(element: KtElement) {
            if (used) return
            element.acceptChildren(this)

            if (isUsageOfSymbol(callableSymbol, element) || isUsageOfReifiedType(usedTypeParametersInReceiver, element)) {
                used = true
            }
        }
    })
    return used
}


/**
 * Returns all type parameters that are being referenced by the [typeReference].
 * If the [typeReference] is removed, then we also want to remove any type parameters that potentially became unused.
 */
private fun typeParameters(typeReference: KtTypeReference): List<KtTypeParameter> {
    val parameterParent = typeReference.getParentOfTypesAndPredicate(
        true,
        KtNamedFunction::class.java, KtProperty::class.java, KtClass::class.java,
    ) { true }
    return typeReference.typeElement
        ?.collectDescendantsOfType<KtNameReferenceExpression>()
        ?.mapNotNull {
            val typeParameter = it.reference?.resolve() as? KtTypeParameter ?: return@mapNotNull null
            val parent = typeParameter.getParentOfTypesAndPredicate(
                true,
                KtNamedFunction::class.java, KtProperty::class.java, KtClass::class.java,
            ) { true }
            if (parent == parameterParent) typeParameter else null
        } ?: emptyList()
}

/**
 * Removes any of the [typeParameters] if they are no longer being referenced.
 */
private fun removeUnusedTypeParameters(typeParameters: List<KtTypeParameter>) {
    val unusedTypeParams = typeParameters.filter { typeParameter ->
        ReferencesSearch.search(typeParameter).asIterable().none { (it as? KtSimpleNameReference)?.expression?.parent !is KtTypeConstraint }
    }
    if (unusedTypeParams.isEmpty()) return
    runWriteAction {
        unusedTypeParams.forEach { typeParameter ->
            val typeParameterList = typeParameter.parent as? KtTypeParameterList ?: return@forEach
            val typeConstraintList = typeParameterList.parent.getChildOfType<KtTypeConstraintList>()
            if (typeConstraintList != null) {
                val typeConstraint = typeConstraintList.constraints.find { it.subjectTypeParameterName?.text == typeParameter.text }
                if (typeConstraint != null) EditCommaSeparatedListHelper.removeItem(typeConstraint)
                if (typeConstraintList.constraints.isEmpty()) {
                    val prev = typeConstraintList.getPrevSiblingIgnoringWhitespaceAndComments()
                    if (prev?.node?.elementType == KtTokens.WHERE_KEYWORD) prev.delete()
                }
            }
            if (typeParameterList.parameters.size == 1)
                typeParameterList.delete()
            else
                EditCommaSeparatedListHelper.removeItem(typeParameter)
        }
    }
}

/**
 * We use this function to check if the callable symbol has a receiver that might potentially be used as a context receiver of this symbol.
 * This is needed because the analysis API does not expose passed context receivers yet: KT-73709
 */
context(KaSession)
@OptIn(KaExperimentalApi::class)
private fun KaCallableMemberCall<*, *>.hasContextReceiverOfType(type: KaType): Boolean {
    val substitutor = buildSubstitutor {
        substitutions(typeArgumentsMapping)
    }
    return symbol.contextReceivers.any { type.isSubtypeOf(substitutor.substitute(it.type)) }
}

/**
 * Returns whether the [element] makes use of one of the [reifiedTypes].
 * This will only return true if the [element] is inside a function body or function expression.
 */
context(KaSession)
private fun isUsageOfReifiedType(reifiedTypes: Set<KaTypeParameterSymbol>, element: KtElement): Boolean {
    val parentFunction = element.parentOfType<KtFunction>() ?: return false
    if (element !is KtExpression) return false
    // It is only a real use if the reified type is used in the body of the function
    if (element.parents.none { it == parentFunction.bodyBlockExpression || it == parentFunction.bodyExpression }) return false
    return reifiedTypes.contains(element.resolveExpression())
}

/**
 * Returns whether the [symbol] is being used by the [element] by referencing it.
 */
context(KaSession)
private fun isUsageOfSymbol(symbol: KaDeclarationSymbol, element: KtElement): Boolean {
    if (element !is KtExpression) return false

    val receiverType = (symbol as? KaCallableSymbol)?.receiverType
    fun isUsageOfSymbolInResolvedCall(resolvedCall: KaCall): Boolean = when (resolvedCall) {
        is KaFunctionCall<*>, is KaVariableAccessCall -> {
            val partiallyAppliedSymbol = resolvedCall.partiallyAppliedSymbol

            partiallyAppliedSymbol.dispatchReceiver?.getThisReceiverOwner() == symbol ||
                    partiallyAppliedSymbol.extensionReceiver?.getThisReceiverOwner() == symbol ||
                    (receiverType != null && resolvedCall.hasContextReceiverOfType(receiverType)) // potentially captured by context receiver
        }

        else -> false
    }

    when (element) {
        is KtClassLiteralExpression -> {
            val typeParameterType = (element.receiverExpression?.mainReference?.resolveToSymbol() as? KaTypeParameterSymbol)?.defaultType
            if (typeParameterType != null && receiverType?.semanticallyEquals(typeParameterType) == true) {
                return true
            }
        }
    }

    fun processOperators(e: KtElement): Boolean {
        val operatorFunctions = e.mainReference?.resolveToSymbols()?.filterIsInstance<KaFunctionSymbol>() ?: return false
        return operatorFunctions.any { receiverType?.symbol == it.containingDeclaration }
    }

    when (element) {
        is KtThisExpression -> { // Check if this refers to our receiver
            val referencedSymbol = element.instanceReference.mainReference.resolveToSymbol()
            return referencedSymbol is KaReceiverParameterSymbol && referencedSymbol.owningCallableSymbol == symbol
        }

        is KtDestructuringDeclarationEntry -> {
            return processOperators(element)
        }

        is KtProperty -> {
            val propertyDelegate = element.delegate
            return propertyDelegate != null && processOperators(propertyDelegate)
        }

        is KtForExpression -> {
            return processOperators(element)
        }

        else -> {
            val resolvedCall = element.resolveToCall()?.successfulCallOrNull<KaCall>() ?: return false
            return isUsageOfSymbolInResolvedCall(resolvedCall)
        }
    }
}
