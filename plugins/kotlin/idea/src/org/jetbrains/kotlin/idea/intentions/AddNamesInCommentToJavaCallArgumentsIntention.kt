// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.hints.AddToExcludeListCurrentMethodIntention
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddNamesInCommentToJavaCallArgumentsIntention: SelfTargetingIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.lazyMessage("add.names.in.comment.to.call.arguments")
) {
    override fun isApplicableTo(element: KtCallExpression, caretOffset: Int): Boolean {
        val arguments = element.valueArguments.filterNot { it is KtLambdaArgument }
        if (arguments.isEmpty() || arguments.any { it.isNamed() || it.hasBlockComment() }) return false
        val context = element.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
        val resolvedCall = element.getResolvedCall(context) ?: return false
        val descriptor = resolvedCall.candidateDescriptor
        if (descriptor !is JavaMethodDescriptor && descriptor !is JavaClassConstructorDescriptor) return false
        val resolvedArguments = arguments.resolve(resolvedCall)
        if (arguments.size != resolvedArguments.size) return false
        return resolvedArguments.none { (argument, _) ->
            val argumentExpression = argument.getArgumentExpression()
            argumentExpression == null || context.diagnostics.forElement(argumentExpression).any { it.severity == Severity.ERROR }
        }
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val resolvedCall = element.resolveToCall() ?: return
        val valueArgumentList = element.valueArgumentList ?: return
        if (editor != null) {
            editor.caretModel.moveToOffset(valueArgumentList.startOffset)
            val addToExcludeListCurrentMethodIntention = AddToExcludeListCurrentMethodIntention()
            val project = element.project
            val file = element.containingKtFile
            if (addToExcludeListCurrentMethodIntention.isAvailable(project, editor, file)) {
                addToExcludeListCurrentMethodIntention.invoke(project, editor, file)
            }
        }
        val psiFactory = KtPsiFactory(element)
        for ((argument, parameter) in element.valueArguments.resolve(resolvedCall)) {
            val isVararg = parameter.isVararg
            val name = if (isVararg) "...${parameter.name}" else parameter.name
            val parent = argument.parent
            parent.addBefore(psiFactory.createComment("/* $name = */"), argument)
            parent.addBefore(psiFactory.createWhiteSpace(), argument)
            if (isVararg) break
        }
    }

    private fun List<KtValueArgument>.resolve(
        resolvedCall: ResolvedCall<out CallableDescriptor>
    ): List<Pair<KtValueArgument, ValueParameterDescriptor>> =
        mapNotNull {
            if (it is KtLambdaArgument) return@mapNotNull null
            val parameter = resolvedCall.getArgumentMapping(it).safeAs<ArgumentMatch>()?.valueParameter ?: return@mapNotNull null
            it to parameter
        }

    private fun KtValueArgument.hasBlockComment(): Boolean =
        siblings(forward = false, withSelf = false)
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .any { it is PsiComment && it.elementType == KtTokens.BLOCK_COMMENT }
}
