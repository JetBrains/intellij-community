// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.idea.codeinsights.impl.base.NameCommentsByArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canAddArgumentNameCommentsByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.getArgumentNameComments
import org.jetbrains.kotlin.idea.codeinsights.impl.base.hasBlockCommentWithName
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddNamesInCommentToJavaCallArgumentsIntention
    : AbstractKotlinModCommandWithContext<KtCallElement, AddNamesInCommentToJavaCallArgumentsIntention.Context>(KtCallElement::class) {

    class Context(val nameCommentsByArgument: NameCommentsByArgument)

    override fun getFamilyName(): String = KotlinBundle.message("add.names.in.comment.to.call.arguments")

    override fun getActionName(element: KtCallElement, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallElement> = ApplicabilityRanges.CALL_EXCLUDING_LAMBDA_ARGUMENT

    override fun isApplicableByPsi(element: KtCallElement): Boolean = element.canAddArgumentNameCommentsByPsi()

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallElement): Context? = getArgumentNameComments(element)?.let { Context(it) }

    override fun apply(element: KtCallElement, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        val nameCommentsMap = context.analyzeContext.nameCommentsByArgument.dereferenceValidKeys()
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