// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.highlighter.markers.KotlinLineMarkerOptions
import org.jetbrains.kotlin.idea.highlighter.markers.LineMarkerInfos
import org.jetbrains.kotlin.idea.inspections.RecursivePropertyAccessorInspection
import org.jetbrains.kotlin.idea.util.getReceiverTargetDescriptor
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

class KotlinRecursiveCallLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = KotlinBundle.message("highlighter.tool.tip.text.recursive.call")
    override fun getIcon() = AllIcons.Gutter.RecursiveMethod

    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        if (!KotlinLineMarkerOptions.recursiveOption.isEnabled) return
        val markedLineNumbers = HashSet<Int>()

        for (element in elements) {
            ProgressManager.checkCanceled()
            if (element is KtElement) {
                val lineNumber = element.getLineNumber()
                if (lineNumber !in markedLineNumbers && isRecursiveCall(element)) {
                    markedLineNumbers.add(lineNumber)
                    result.add(RecursiveMethodCallMarkerInfo(getElementForLineMark(element)))
                }
            }
        }
    }

    private fun getEnclosingFunction(element: KtElement, stopOnNonInlinedLambdas: Boolean): KtNamedFunction? {
        for (parent in element.parents) {
            when (parent) {
                is KtFunctionLiteral -> if (stopOnNonInlinedLambdas && !InlineUtil.isInlinedArgument(
                        parent,
                        parent.analyze(),
                        false
                    )
                ) return null
                is KtNamedFunction -> {
                    when (parent.parent) {
                        is KtBlockExpression, is KtClassBody, is KtFile, is KtScript -> return parent
                        else -> if (stopOnNonInlinedLambdas && !InlineUtil.isInlinedArgument(parent, parent.analyze(), false)) return null
                    }
                }
                is KtClassOrObject -> return null
            }
        }
        return null
    }

    private fun isRecursiveCall(element: KtElement): Boolean {
        if (RecursivePropertyAccessorInspection.isRecursivePropertyAccess(element, anyRecursionTypes = true)) return true
        if (RecursivePropertyAccessorInspection.isRecursiveSyntheticPropertyAccess(element)) return true
        // Fast check for names without resolve
        val resolveName = getCallNameFromPsi(element) ?: return false
        val enclosingFunction = getEnclosingFunction(element, false) ?: return false

        val enclosingFunctionName = enclosingFunction.name
        if (enclosingFunctionName != OperatorNameConventions.INVOKE.asString()
            && enclosingFunctionName != resolveName.asString()
        ) return false

        // Check that there were no not-inlined lambdas on the way to enclosing function
        if (enclosingFunction != getEnclosingFunction(element, true)) return false

        val bindingContext = element.safeAnalyzeNonSourceRootCode()
        val enclosingFunctionDescriptor = bindingContext[BindingContext.FUNCTION, enclosingFunction] ?: return false

        val call = bindingContext[BindingContext.CALL, element] ?: return false
        val resolvedCall = bindingContext[BindingContext.RESOLVED_CALL, call] ?: return false

        if (resolvedCall.candidateDescriptor.original != enclosingFunctionDescriptor) return false

        fun isDifferentReceiver(receiver: Receiver?): Boolean {
            if (receiver !is ReceiverValue) return false

            val receiverOwner = receiver.getReceiverTargetDescriptor(bindingContext) ?: return true

            return when (receiverOwner) {
                is SimpleFunctionDescriptor -> receiverOwner != enclosingFunctionDescriptor
                is ClassDescriptor -> receiverOwner != enclosingFunctionDescriptor.containingDeclaration
                is PropertyDescriptor -> receiverOwner.containingDeclaration != enclosingFunctionDescriptor.containingDeclaration
                else -> return true
            }
        }

        return !isDifferentReceiver(resolvedCall.dispatchReceiver)
    }

    private class RecursiveMethodCallMarkerInfo(callElement: PsiElement) : LineMarkerInfo<PsiElement>(
        callElement,
        callElement.textRange,
        AllIcons.Gutter.RecursiveMethod,
        { KotlinBundle.message("highlighter.tool.tip.text.recursive.call") },
        null,
        GutterIconRenderer.Alignment.RIGHT,
        { KotlinBundle.message("highlighter.tool.tip.text.recursive.call") }
    ) {

        override fun createGutterRenderer(): GutterIconRenderer {
            return object : LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>(this) {
                override fun getClickAction() = null // to place breakpoint on mouse click
            }
        }
    }

}

internal fun getElementForLineMark(callElement: PsiElement): PsiElement = when (callElement) {
    is KtSimpleNameExpression -> callElement.getReferencedNameElement()
    else ->
        // a fallback,
        //but who knows what to reference in KtArrayAccessExpression ?
        generateSequence(callElement) { it.firstChild }.last()
}

private fun PsiElement.getLineNumber(): Int {
    return PsiDocumentManager.getInstance(project).getDocument(containingFile)!!.getLineNumber(textOffset)
}

private fun getCallNameFromPsi(element: KtElement): Name? {
    when (element) {
        is KtSimpleNameExpression -> {
            val identifier = lazy { Name.identifier(element.text.unquoteKotlinIdentifier()) }
            when (val elementParent = element.getParent()) {
                is KtCallExpression -> return identifier.value
                is KtOperationExpression -> {
                    val operationReference = elementParent.operationReference
                    if (element == operationReference) {
                        val node = operationReference.getReferencedNameElementType()
                        return if (node is KtToken) {
                            val conventionName = if (elementParent is KtPrefixExpression)
                                OperatorConventions.getNameForOperationSymbol(node, true, false)
                            else
                                OperatorConventions.getNameForOperationSymbol(node)

                            conventionName ?: identifier.value
                        } else {
                            identifier.value
                        }
                    }
                }
            }
        }

        is KtArrayAccessExpression -> return OperatorNameConventions.GET
        is KtThisExpression -> if (element.getParent() is KtCallExpression) return OperatorNameConventions.INVOKE
    }

    return null
}
