// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.idea.codeinsights.impl.base.NameCommentsByArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canAddArgumentNameCommentsByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getArgumentNameComments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasBlockCommentWithName
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddNamesInCommentToJavaCallArgumentsIntention :
    KotlinApplicableModCommandAction<KtCallElement, AddNamesInCommentToJavaCallArgumentsIntention.Context>(KtCallElement::class) {

    data class Context(
        val nameCommentsByArgument: NameCommentsByArgument,
    )

    override fun getFamilyName(): String = KotlinBundle.message("add.names.in.comment.to.call.arguments")

    override fun getApplicableRanges(element: KtCallElement): List<TextRange> =
        ApplicabilityRanges.callExcludingLambdaArgument(element)

    override fun isApplicableByPsi(element: KtCallElement): Boolean = element.canAddArgumentNameCommentsByPsi()

    override fun KaSession.prepareContext(element: KtCallElement): Context? = getArgumentNameComments(element)?.let { Context(it) }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallElement,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        val nameCommentsMap = elementContext.nameCommentsByArgument.dereferenceValidKeys()
        val psiFactory = KtPsiFactory(element)
        element.valueArguments.filterIsInstance<KtValueArgument>().forEach { argument ->
            // If the argument already has a name comment (regardless of whether it has the correct argument name), don't add another
            // comment to it. Note that wrong argument names are covered by `InconsistentCommentForJavaParameterInspection`.
            if (argument.hasBlockCommentWithName()) return@forEach

            val comment = nameCommentsMap[argument]?.comment ?: return@forEach
            val parent = argument.parent
            parent.addBefore(psiFactory.createComment(comment), argument)
            parent.addBefore(psiFactory.createWhiteSpace(), argument)
        }
    }
}