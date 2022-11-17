// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.descriptors.accessors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.highlighter.markers.LineMarkerInfos
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContext.*
import org.jetbrains.kotlin.resolve.calls.checkers.isBuiltInCoroutineContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinSuspendCallLineMarkerProvider : LineMarkerProvider {
    private class SuspendCallMarkerInfo(callElement: PsiElement, message: String) : LineMarkerInfo<PsiElement>(
        callElement,
        callElement.textRange,
        KotlinIcons.SUSPEND_CALL,
        { message },
        null,
        GutterIconRenderer.Alignment.RIGHT,
        { message }
    ) {
        override fun createGutterRenderer(): GutterIconRenderer {
            return object : LineMarkerGutterIconRenderer<PsiElement>(this) {
                override fun getClickAction(): AnAction? = null
            }
        }
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: LineMarkerInfos) {
        val markedLineNumbers = HashSet<Int>()

        for (element in elements) {
            ProgressManager.checkCanceled()

            if (element !is KtExpression) continue

            val containingFile = element.containingFile
            if (containingFile !is KtFile || containingFile is KtCodeFragment) continue

            val lineNumber = element.getLineNumber()
            if (lineNumber in markedLineNumbers) continue

            val kind = getSuspendCallKind(element, element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) ?: continue
            val anchor = kind.anchor ?: continue

            markedLineNumbers += lineNumber
            result += SuspendCallMarkerInfo(getElementForLineMark(anchor), kind.description)
        }
    }
}

sealed class SuspendCallKind<T : KtExpression>(val element: T) {
    class Iteration(element: KtForExpression) : SuspendCallKind<KtForExpression>(element) {
        override val anchor get() = element.loopRange
        override val description get() = KotlinBundle.message("highlighter.message.suspending.iteration")
    }

    class FunctionCall(element: KtCallExpression) : SuspendCallKind<KtCallExpression>(element) {
        override val anchor get() = element.calleeExpression
        override val description get() = KotlinBundle.message("highlighter.message.suspend.function.call")
    }

    class PropertyDelegateCall(element: KtProperty) : SuspendCallKind<KtProperty>(element) {
        override val anchor get() = element.delegate?.node?.findChildByType(KtTokens.BY_KEYWORD) as? PsiElement ?: element.delegate
        override val description get() = KotlinBundle.message("highlighter.message.suspend.function.call")
    }

    class NameCall(element: KtSimpleNameExpression) : SuspendCallKind<KtSimpleNameExpression>(element) {
        override val anchor get() = element
        override val description get() = KotlinBundle.message("highlighter.message.suspend.function.call")
    }

    abstract val anchor: PsiElement?
    abstract val description: String
}

fun getSuspendCallKind(expression: KtExpression, bindingContext: BindingContext): SuspendCallKind<*>? {
    fun isSuspend(descriptor: CallableDescriptor): Boolean = when (descriptor) {
        is FunctionDescriptor -> descriptor.isSuspend
        is PropertyDescriptor -> descriptor.accessors.any { it.isSuspend } || descriptor.isBuiltInCoroutineContext()
        else -> false
    }

    fun isSuspend(resolvedCall: ResolvedCall<*>): Boolean {
        if (resolvedCall is VariableAsFunctionResolvedCall) {
            return isSuspend(resolvedCall.variableCall.resultingDescriptor) || isSuspend(resolvedCall.functionCall.resultingDescriptor)
        }

        return isSuspend(resolvedCall.resultingDescriptor)
    }

    when {
        expression is KtForExpression -> {
            val loopRange = expression.loopRange
            val iteratorResolvedCall = bindingContext[LOOP_RANGE_ITERATOR_RESOLVED_CALL, loopRange]
            val hasNextResolvedCall = bindingContext[LOOP_RANGE_HAS_NEXT_RESOLVED_CALL, loopRange]
            val nextResolvedCall = bindingContext[LOOP_RANGE_NEXT_RESOLVED_CALL, loopRange]
            val isSuspend = listOf(iteratorResolvedCall, hasNextResolvedCall, nextResolvedCall)
                .any { it?.resultingDescriptor?.isSuspend == true }

            if (isSuspend) {
                return SuspendCallKind.Iteration(expression)
            }
        }

        expression is KtProperty && expression.hasDelegateExpression() -> {
            val variableDescriptor = bindingContext[DECLARATION_TO_DESCRIPTOR, expression] as? VariableDescriptorWithAccessors
            val accessors = variableDescriptor?.accessors ?: emptyList()
            val isSuspend = accessors.any { accessor ->
                val delegatedFunctionDescriptor = bindingContext[DELEGATED_PROPERTY_RESOLVED_CALL, accessor]?.resultingDescriptor
                delegatedFunctionDescriptor?.isSuspend == true
            }

            if (isSuspend) {
                return SuspendCallKind.PropertyDelegateCall(expression)
            }
        }

        expression is KtCallExpression -> {
            val resolvedCall = expression.getResolvedCall(bindingContext)
            if (resolvedCall != null && isSuspend(resolvedCall)) {
                return SuspendCallKind.FunctionCall(expression)
            }
        }

        expression is KtOperationReferenceExpression -> {
            val resolvedCall = expression.getResolvedCall(bindingContext)
            if (resolvedCall != null && isSuspend(resolvedCall)) {
                return SuspendCallKind.NameCall(expression)
            }
        }

        expression is KtNameReferenceExpression -> {
            val resolvedCall = expression.getResolvedCall(bindingContext)
            // Function calls should be handled by KtCallExpression branch
            if (resolvedCall != null && resolvedCall.resultingDescriptor !is FunctionDescriptor && isSuspend(resolvedCall)) {
                return SuspendCallKind.NameCall(expression)
            }
        }
    }

    return null
}
