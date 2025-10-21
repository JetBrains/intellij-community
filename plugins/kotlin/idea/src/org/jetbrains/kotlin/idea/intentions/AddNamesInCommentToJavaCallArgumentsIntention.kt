// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Holder.hasBlockCommentWithName
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Holder.resolve
import org.jetbrains.kotlin.idea.intentions.AddNamesInCommentToJavaCallArgumentsIntention.Holder.toParameterNameComment
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddNamesInCommentToJavaCallArgumentsIntention : SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.messagePointer("add.names.in.comment.to.call.arguments")
) {
    override fun isApplicableTo(element: KtCallElement, caretOffset: Int): Boolean =
        Holder.resolveValueParameterDescriptors(element, canAddNameComments = true) != null

    override fun applyTo(element: KtCallElement, editor: Editor?) {
        val resolvedCall = element.resolveToCall() ?: return
        val psiFactory = KtPsiFactory(element.project)
        for ((argument, parameter) in element.valueArguments.filterIsInstance<KtValueArgument>().resolve(resolvedCall)) {
            if (argument.hasBlockCommentWithName()) continue
            val parent = argument.parent
            parent.addBefore(psiFactory.createComment(parameter.toParameterNameComment()), argument)
            parent.addBefore(psiFactory.createWhiteSpace(), argument)
            if (parameter.isVararg) break
        }
    }

    object Holder {
        fun resolveValueParameterDescriptors(
            element: KtCallElement,
            canAddNameComments: Boolean
        ): List<Pair<KtValueArgument, ValueParameterDescriptor>>? {
            val arguments = element.valueArguments.filterIsInstance<KtValueArgument>().filterNot { it is KtLambdaArgument }
            if (arguments.isEmpty() || arguments.any { it.isNamed() } ||
                (canAddNameComments && arguments.all { it.hasBlockCommentWithName() }) ||
                (!canAddNameComments && arguments.none { it.hasBlockCommentWithName() })
            ) return null
            val resolvedCall = element.resolveToCall() ?: return null
            val descriptor = resolvedCall.candidateDescriptor
            if (descriptor !is JavaMethodDescriptor && descriptor !is JavaClassConstructorDescriptor) return null
            val resolve = arguments.resolve(resolvedCall)
            if (arguments.size != resolve.size) return null
            return resolve
        }

        fun ValueParameterDescriptor.toParameterNameComment(): String =
            canonicalParameterNameComment(if (isVararg) "...$name" else name.asString())

        private fun canonicalParameterNameComment(parameterName: String): String = "/* $parameterName = */"

        fun PsiComment.isParameterNameComment(parameter: ValueParameterDescriptor): Boolean {
            if (this.elementType != KtTokens.BLOCK_COMMENT) return false
            val parameterName = text
                .removePrefix("/*").removeSuffix("*/").trim()
                .takeIf { it.endsWith("=") }?.removeSuffix("=")?.trim()
                ?: return false
            return canonicalParameterNameComment(parameterName) == parameter.toParameterNameComment()
        }

        fun KtValueArgument.hasBlockCommentWithName(): Boolean =
            blockCommentWithName() != null

        fun KtValueArgument.blockCommentWithName(): PsiComment? =
            siblings(forward = false, withSelf = false)
                .takeWhile { it is PsiWhiteSpace || it is PsiComment }
                .filterIsInstance<PsiComment>()
                .firstOrNull { it.elementType == KtTokens.BLOCK_COMMENT && it.text.removeSuffix("*/").trim().endsWith("=") }

        fun List<KtValueArgument>.resolve(
            resolvedCall: ResolvedCall<out CallableDescriptor>
        ): List<Pair<KtValueArgument, ValueParameterDescriptor>> =
            mapNotNull {
                if (it is KtLambdaArgument) return@mapNotNull null
                val parameter = resolvedCall.getArgumentMapping(it).safeAs<ArgumentMatch>()?.valueParameter ?: return@mapNotNull null
                it to parameter
            }
    }
}
