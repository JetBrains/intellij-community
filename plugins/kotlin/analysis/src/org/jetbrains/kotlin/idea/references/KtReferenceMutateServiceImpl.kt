/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.references

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName
import org.jetbrains.kotlin.idea.codeInsight.shorten.addDelayedImportRequest
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.intentions.OperatorToFunctionIntention
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.plugin.references.SimpleNameReferenceExtension
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DataClassDescriptorResolver
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

class KtReferenceMutateServiceImpl : KtReferenceMutateService {
    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement = when (ktReference) {
        is KtSimpleNameReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.DELAYED_SHORTENING)
        else -> throw IncorrectOperationException()
    }

    override fun bindToElement(
        simpleNameReference: KtSimpleNameReference,
        element: PsiElement,
        shorteningMode: KtSimpleNameReference.ShorteningMode
    ): PsiElement {
        return element.getKotlinFqName()?.let { fqName -> bindToFqName(simpleNameReference, fqName, shorteningMode, element) }
            ?: simpleNameReference.expression
    }

    override fun bindToFqName(
        simpleNameReference: KtSimpleNameReference,
        fqName: FqName,
        shorteningMode: KtSimpleNameReference.ShorteningMode,
        targetElement: PsiElement?
    ): PsiElement {
        val expression = simpleNameReference.expression
        if (fqName.isRoot) return expression

        // not supported for infix calls and operators
        if (expression !is KtNameReferenceExpression) return expression
        if (expression.parent is KtThisExpression || expression.parent is KtSuperExpression) return expression // TODO: it's a bad design of PSI tree, we should change it

        val newExpression = expression.changeQualifiedName(
            fqName.quoteIfNeeded().let {
                if (shorteningMode == KtSimpleNameReference.ShorteningMode.NO_SHORTENING)
                    it
                else
                    it.withRootPrefixIfNeeded(expression)
            },
            targetElement
        )
        val newQualifiedElement = newExpression.getQualifiedElementOrCallableRef()

        if (shorteningMode == KtSimpleNameReference.ShorteningMode.NO_SHORTENING) return newExpression

        val needToShorten = PsiTreeUtil.getParentOfType(expression, KtImportDirective::class.java, KtPackageDirective::class.java) == null
        if (!needToShorten) {
            return newExpression
        }

        return if (shorteningMode == KtSimpleNameReference.ShorteningMode.FORCED_SHORTENING || !isDispatchThread()) {
            ShortenReferences.DEFAULT.process(newQualifiedElement)
        } else {
            newQualifiedElement.addToShorteningWaitSet()
            newExpression
        }
    }

    override fun handleElementRename(ktReference: KtReference, newElementName: String): PsiElement {
        return when (ktReference) {
            is KtArrayAccessReference -> ktReference.renameImplicitConventionalCall(newElementName)
            is KtInvokeFunctionReference -> with(ktReference) {
                val callExpression = expression
                val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
                if (newElementName == OperatorNameConventions.GET.asString() && callExpression.typeArguments.isEmpty()) {
                    val arrayAccessExpression = KtPsiFactory(callExpression).buildExpression {
                        if (fullCallExpression is KtQualifiedExpression) {
                            appendExpression(fullCallExpression.receiverExpression)
                            appendFixedText(fullCallExpression.operationSign.value)
                        }
                        appendExpression(callExpression.calleeExpression)
                        appendFixedText("[")
                        appendExpressions(callExpression.valueArguments.map { it.getArgumentExpression() })
                        appendFixedText("]")
                    }
                    return fullCallExpression.replace(arrayAccessExpression)
                }
                renameImplicitConventionalCall(newElementName)
            }
            is KtSimpleNameReference -> with(ktReference) {
                if (!canRename()) throw IncorrectOperationException()

                if (newElementName.unquote() == "") {
                    return when (val qualifiedElement = expression.getQualifiedElement()) {
                        is KtQualifiedExpression -> {
                            expression.replace(qualifiedElement.receiverExpression)
                            qualifiedElement.replaced(qualifiedElement.selectorExpression!!)
                        }
                        is KtUserType -> expression.replaced(
                            KtPsiFactory(expression).createSimpleName(
                                SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT.asString()
                            )
                        )
                        else -> expression
                    }
                }

                // Do not rename if the reference corresponds to synthesized component function
                val expressionText = expression.text
                if (expressionText != null && Name.isValidIdentifier(expressionText)) {
                    if (DataClassDescriptorResolver.isComponentLike(Name.identifier(expressionText)) && resolve() is KtParameter) {
                        return expression
                    }
                }

                val psiFactory = KtPsiFactory(expression)
                val element = expression.project.extensionArea.getExtensionPoint(SimpleNameReferenceExtension.EP_NAME).extensions
                    .asSequence()
                    .map { it.handleElementRename(this, psiFactory, newElementName) }
                    .firstOrNull { it != null } ?: psiFactory.createNameIdentifier(newElementName.quoteIfNeeded())

                val nameElement = expression.getReferencedNameElement()

                val elementType = nameElement.node.elementType
                if (elementType is KtToken && OperatorConventions.getNameForOperationSymbol(elementType) != null) {
                    val opExpression = expression.parent as? KtOperationExpression
                    if (opExpression != null) {
                        val (newExpression, newNameElement) = OperatorToFunctionIntention.convert(opExpression)
                        newNameElement.replace(element)
                        return newExpression
                    }
                }

                if (element.node.elementType == KtTokens.IDENTIFIER) {
                    nameElement.astReplace(element)
                } else {
                    nameElement.replace(element)
                }
                return expression
            }
            else -> throw IncorrectOperationException()
        }
    }

    /**
     * Replace [[KtNameReferenceExpression]] (and its enclosing qualifier) with qualified element given by FqName
     * Result is either the same as original element, or [[KtQualifiedExpression]], or [[KtUserType]]
     * Note that FqName may not be empty
     */
    private fun KtNameReferenceExpression.changeQualifiedName(
        fqName: FqName,
        targetElement: PsiElement? = null
    ): KtNameReferenceExpression {
        assert(!fqName.isRoot) { "Can't set empty FqName for element $this" }

        val shortName = fqName.shortName().asString()
        val psiFactory = KtPsiFactory(this)
        val parent = parent

        if (parent is KtUserType && !fqName.isOneSegmentFQN()) {
            val qualifier = parent.qualifier
            val qualifierReference = qualifier?.referenceExpression as? KtNameReferenceExpression
            if (qualifierReference != null && qualifier.typeArguments.isNotEmpty()) {
                qualifierReference.changeQualifiedName(fqName.parent(), targetElement)
                return this
            }
        }

        val targetUnwrapped = targetElement?.unwrapped

        if (targetUnwrapped != null && targetUnwrapped.isTopLevelKtOrJavaMember() && fqName.isOneSegmentFQN()) {
            addDelayedImportRequest(targetUnwrapped, containingKtFile)
        }

        var parentDelimiter = "."
        val fqNameBase = when {
            parent is KtCallElement -> {
                val callCopy = parent.copied()
                callCopy.calleeExpression!!.replace(psiFactory.createSimpleName(shortName)).parent!!.text
            }
            parent is KtCallableReferenceExpression && parent.callableReference == this -> {
                parentDelimiter = ""
                val callableRefCopy = parent.copied()
                callableRefCopy.receiverExpression?.delete()
                val newCallableRef = callableRefCopy
                    .callableReference
                    .replace(psiFactory.createSimpleName(shortName))
                    .parent as KtCallableReferenceExpression
                if (targetUnwrapped != null && targetUnwrapped.isTopLevelKtOrJavaMember()) {
                    addDelayedImportRequest(targetUnwrapped, parent.containingKtFile)
                    return parent.replaced(newCallableRef).callableReference as KtNameReferenceExpression
                }
                newCallableRef.text
            }
            else -> shortName
        }

        val text = if (!fqName.isOneSegmentFQN()) "${fqName.parent().asString()}$parentDelimiter$fqNameBase" else fqNameBase

        val elementToReplace = getQualifiedElementOrCallableRef()

        val newElement = when (elementToReplace) {
            is KtUserType -> {
                val typeText = "$text${elementToReplace.typeArgumentList?.text ?: ""}"
                elementToReplace.replace(psiFactory.createType(typeText).typeElement!!)
            }
            else -> KtPsiUtil.safeDeparenthesize(elementToReplace.replaced(psiFactory.createExpression(text)))
        } as KtElement

        val selector = (newElement as? KtCallableReferenceExpression)?.callableReference
            ?: newElement.getQualifiedElementSelector()
            ?: error("No selector for $newElement")
        return selector as KtNameReferenceExpression
    }
}