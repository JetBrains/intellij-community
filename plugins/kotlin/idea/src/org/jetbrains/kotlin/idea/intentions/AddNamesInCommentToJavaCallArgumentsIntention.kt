// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddNamesInCommentToJavaCallArgumentsIntention : SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.lazyMessage("add.names.in.comment.to.call.arguments")
) {
    override fun isApplicableTo(element: KtCallElement, caretOffset: Int): Boolean =
        resolveValueParameterDescriptors(element, anyBlockCommentsWithName = true) != null

    override fun applyTo(element: KtCallElement, editor: Editor?) {
        val resolvedCall = element.resolveToCall() ?: return
        val psiFactory = KtPsiFactory(element)
        for ((argument, parameter) in element.valueArguments.filterIsInstance<KtValueArgument>().resolve(resolvedCall)) {
            val parent = argument.parent
            parent.addBefore(psiFactory.createComment(parameter.toCommentedParameterName()), argument)
            parent.addBefore(psiFactory.createWhiteSpace(), argument)
            if (parameter.isVararg) break
        }
    }

    companion object {
        fun resolveValueParameterDescriptors(
            element: KtCallElement,
            anyBlockCommentsWithName: Boolean
        ): List<Pair<KtValueArgument, ValueParameterDescriptor>>? {
            val arguments = element.valueArguments.filterIsInstance<KtValueArgument>().filterNot { it is KtLambdaArgument }
            if (arguments.isEmpty() || arguments.any { it.isNamed() } ||
                (anyBlockCommentsWithName && arguments.any { it.hasBlockCommentWithName() }) ||
                (!anyBlockCommentsWithName && arguments.none { it.hasBlockCommentWithName() })
            ) return null
            val resolvedCall = element.resolveToCall() ?: return null
            val descriptor = resolvedCall.candidateDescriptor
            if (descriptor !is JavaMethodDescriptor && descriptor !is JavaClassConstructorDescriptor) return null
            val resolve = arguments.resolve(resolvedCall)
            if (arguments.size != resolve.size) return null
            return resolve
        }

        fun ValueParameterDescriptor.toCommentedParameterName(): String =
            "/* ${if (isVararg) "...$name" else name.asString()} = */"

        fun KtValueArgument.hasBlockCommentWithName(): Boolean =
            blockCommentWithName() != null

        fun KtValueArgument.blockCommentWithName(): PsiComment? =
            siblings(forward = false, withSelf = false)
                .takeWhile { it is PsiWhiteSpace || it is PsiComment }
                .filterIsInstance<PsiComment>()
                .firstOrNull { it.elementType == KtTokens.BLOCK_COMMENT && it.text.endsWith("= */") }

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
