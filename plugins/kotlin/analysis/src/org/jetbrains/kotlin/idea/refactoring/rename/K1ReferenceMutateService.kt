// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.shorten.addDelayedImportRequest
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.idea.references.SyntheticPropertyAccessorReference
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class K1ReferenceMutateService : KtReferenceMutateServiceBase() {
    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement = when (ktReference) {
        is KtSimpleNameReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.DELAYED_SHORTENING)
        else -> throw IncorrectOperationException()
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

    override fun handleElementRename(ktReference: KtReference,
                                     newElementName: String): PsiElement? {
        if (ktReference is SyntheticPropertyAccessorReference) {
            return ktReference.renameTo(newElementName)
        }
        return super.handleElementRename(ktReference, newElementName)
    }

    private fun SyntheticPropertyAccessorReference.renameTo(newElementName: String): KtElement? {
        if (!Name.isValidIdentifier(newElementName)) return expression

        val newName = getAdjustedNewName(newElementName)
        // get/set becomes ordinary method
        if (newName == null) {
            return renameToOrdinaryMethod(newElementName)
        }

        return renameByPropertyName(newName.identifier)
    }

    override fun KtSimpleReference<KtNameReferenceExpression>.suggestVariableName(expr: KtExpression, context: PsiElement): String {
        val anchor = expr.parent.parentsWithSelf.firstOrNull { it.parent == context }
        val validator = Fe10KotlinNewDeclarationNameValidator(
            context,
            anchor,
            KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
        )
        return Fe10KotlinNameSuggester.suggestNamesByExpressionAndType(
            expr,
            null,
            expr.analyze(),
            validator,
            "p"
        ).first()
    }

    private fun SyntheticPropertyAccessorReference.renameByPropertyName(newName: String): KtNameReferenceExpression {
        val nameIdentifier = KtPsiFactory(expression.project).createNameIdentifier(newName)
        expression.getReferencedNameElement().replace(nameIdentifier)
        return expression
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
        val psiFactory = KtPsiFactory(project)
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

    override fun canMoveLambdaOutsideParentheses(callExpression: KtCallExpression?): Boolean {
        return callExpression?.canMoveLambdaOutsideParentheses() == true
    }
}