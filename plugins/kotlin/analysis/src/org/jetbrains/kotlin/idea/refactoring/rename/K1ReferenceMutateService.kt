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
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.quoteIfNeeded
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.shorten.addDelayedImportRequest
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.core.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.idea.intentions.OperatorToFunctionIntention
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.load.java.propertyNameBySetMethodName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

class K1ReferenceMutateService : KtReferenceMutateServiceBase() {
    override fun bindToElement(ktReference: KtReference, element: PsiElement): PsiElement = when (ktReference) {
        is KtSimpleNameReference -> bindToElement(ktReference, element, KtSimpleNameReference.ShorteningMode.DELAYED_SHORTENING)
        else -> throw IncorrectOperationException()
    }

    override fun bindToElement(
      simpleNameReference: KtSimpleNameReference,
      element: PsiElement,
      shorteningMode: KtSimpleNameReference.ShorteningMode
    ): PsiElement {
        return element.kotlinFqName?.let { fqName -> bindToFqName(simpleNameReference, fqName, shorteningMode, element) }
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

    override fun KtArrayAccessReference.renameTo(newElementName: String): KtExpression {
        return renameImplicitConventionalCall(newElementName)
    }

    override fun KDocReference.renameTo(newElementName: String): PsiElement? {
        val textRange = element.getNameTextRange()
        val newText = textRange.replace(element.text, newElementName)
        val newLink = KDocElementFactory(element.project).createNameFromText(newText)
        return element.replace(newLink)
    }

    override fun KtInvokeFunctionReference.renameTo(newElementName: String): PsiElement {
        val callExpression = expression
        val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
        if (newElementName == OperatorNameConventions.GET.asString() && callExpression.typeArguments.isEmpty()) {
            val arrayAccessExpression = KtPsiFactory(callExpression.project).buildExpression {
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
        return renameImplicitConventionalCall(newElementName)
    }

    override fun SyntheticPropertyAccessorReference.renameTo(newElementName: String): KtElement? {
        if (!Name.isValidIdentifier(newElementName)) return expression

        val newNameAsName = Name.identifier(newElementName)
        val newName = if (getter) {
          propertyNameByGetMethodName(newNameAsName)
        } else {
            //TODO: it's not correct
            //TODO: setIsY -> setIsIsY bug
          propertyNameBySetMethodName(
            newNameAsName,
            withIsPrefix = expression.getReferencedNameAsName().asString().startsWith("is")
          )
        }
        // get/set becomes ordinary method
        if (newName == null) {
            val psiFactory = KtPsiFactory(expression.project)

            val newGetterName = if (getter) newElementName else JvmAbi.getterName(expression.getReferencedName())

            if (expression.readWriteAccess(false) == ReferenceAccess.READ) {
                return expression.replaced(expression.createCall(psiFactory, newGetterName))
            }

            val newSetterName = if (getter) JvmAbi.setterName(expression.getReferencedName()) else newElementName

            val fullExpression = expression.getQualifiedExpressionForSelectorOrThis()
            fullExpression.getAssignmentByLHS()?.let { assignment ->
                val rhs = assignment.right ?: return expression
                val operationToken = assignment.operationToken as? KtSingleValueToken ?: return expression
                val counterpartOp = OperatorConventions.ASSIGNMENT_OPERATION_COUNTERPARTS[operationToken]
                val setterArgument = if (counterpartOp != null) {
                    val getterCall = if (getter) fullExpression.createCall(psiFactory, newGetterName) else fullExpression
                    psiFactory.createExpressionByPattern("$0 ${counterpartOp.value} $1", getterCall, rhs)
                } else {
                    rhs
                }
                val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, setterArgument)
                return assignment.replaced(newSetterCall).getQualifiedElementSelector()
            }

            fullExpression.getStrictParentOfType<KtUnaryExpression>()?.let { unaryExpr ->
                val operationToken = unaryExpr.operationToken as? KtSingleValueToken ?: return expression
                if (operationToken !in OperatorConventions.INCREMENT_OPERATIONS) return expression
                val operationName = OperatorConventions.getNameForOperationSymbol(operationToken)
                val originalValue = if (getter) fullExpression.createCall(psiFactory, newGetterName) else fullExpression
                val incDecValue = psiFactory.createExpressionByPattern("$0.$operationName()", originalValue)
                val parent = unaryExpr.parent
                val context = parent.parentsWithSelf.firstOrNull { it is KtBlockExpression || it is KtDeclarationContainer }
                if (context == parent || context == null) {
                    val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, incDecValue)
                    return unaryExpr.replaced(newSetterCall).getQualifiedElementSelector()
                } else {
                    val anchor = parent.parentsWithSelf.firstOrNull { it.parent == context }
                    val validator = Fe10KotlinNewDeclarationNameValidator(
                      context,
                      anchor,
                      KotlinNameSuggestionProvider.ValidatorTarget.VARIABLE
                    )
                    val varName = Fe10KotlinNameSuggester.suggestNamesByExpressionAndType(
                      unaryExpr,
                      null,
                      unaryExpr.analyze(),
                      validator,
                      "p"
                    ).first()
                    val isPrefix = unaryExpr is KtPrefixExpression
                    val varInitializer = if (isPrefix) incDecValue else originalValue
                    val newVar = psiFactory.createDeclarationByPattern<KtProperty>("val $varName = $0", varInitializer)
                    val setterArgument = psiFactory.createExpression(if (isPrefix) varName else "$varName.$operationName()")
                    val newSetterCall = fullExpression.createCall(psiFactory, newSetterName, setterArgument)
                    val newLine = psiFactory.createNewLine()
                    context.addBefore(newVar, anchor)
                    context.addBefore(newLine, anchor)
                    context.addBefore(newSetterCall, anchor)
                    return unaryExpr.replaced(psiFactory.createExpression(varName))
                }
            }

            return expression
        }

        return renameByPropertyName(newName.identifier)
    }

    override fun KtDefaultAnnotationArgumentReference.renameTo(newElementName: String): KtValueArgument {
        val psiFactory = KtPsiFactory(expression.project)
        val newArgument = psiFactory.createArgument(
          expression.getArgumentExpression(),
          Name.identifier(newElementName.quoteIfNeeded()),
          expression.getSpreadElement() != null
        )
        return expression.replaced(newArgument)
    }

    override fun convertOperatorToFunctionCall(opExpression: KtOperationExpression): Pair<KtExpression, KtSimpleNameExpression> =
      OperatorToFunctionIntention.convert(opExpression)

    private fun KtExpression.createCall(
      psiFactory: KtPsiFactory,
      newName: String? = null,
      argument: KtExpression? = null
    ): KtExpression {
        return if (this is KtQualifiedExpression) {
            copied().also {
                val selector = it.getQualifiedElementSelector() as? KtExpression
                selector?.replace(selector.createCall(psiFactory, newName, argument))
            }
        } else {
            psiFactory.buildExpression {
                if (newName != null) {
                    appendFixedText(newName)
                } else {
                    appendExpression(this@createCall)
                }
                appendFixedText("(")
                if (argument != null) {
                    appendExpression(argument)
                }
                appendFixedText(")")
            }
        }
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
}

internal fun AbstractKtReference<out KtExpression>.renameImplicitConventionalCall(newName: String): KtExpression {
    val (newExpression, newNameElement) = OperatorToFunctionIntention.convert(expression)
    if (OperatorNameConventions.INVOKE.asString() == newName && newExpression is KtDotQualifiedExpression) {
        val canMoveLambda = newExpression.getPossiblyQualifiedCallExpression()?.canMoveLambdaOutsideParentheses() == true
        OperatorToFunctionIntention.replaceExplicitInvokeCallWithImplicit(newExpression)?.let { newQualifiedExpression ->
            newQualifiedExpression.getPossiblyQualifiedCallExpression()
                ?.takeIf { canMoveLambda }
                ?.let(KtCallExpression::moveFunctionLiteralOutsideParentheses)

            return newQualifiedExpression
        }
    }

    newNameElement.mainReference.handleElementRename(newName)
    return newExpression
}