// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ParameterNamesHighlightingVisitor(
    holder: HighlightInfoHolder,
    private val session: KaSession? = null
) : AbstractHighlightingVisitor(holder), DumbAware {
    private val explicitContextArgumentsEnabled =
        (holder.contextFile as? KtFile)
            ?.languageVersionSettings
            ?.supportsFeature(LanguageFeature.ExplicitContextArguments) == true

    @OptIn(KaExperimentalApi::class)
    override fun visitArgument(argument: KtValueArgument) {
        if (!explicitContextArgumentsEnabled) {
            visitPlainArgument(argument)
            return
        }

        val kaSession = session ?: return
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        val parent = argument.parent
        val infoType = if (parent is KtValueArgumentList && parent.parent is KtAnnotationEntry) {
            KotlinHighlightInfoTypeSemanticNames.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
        } else {
            val contextArgument = run {
                val callExpression = parent.parent as? KtCallExpression ?: return@run false
                val argumentExpression = argument.getArgumentExpression()
                context(kaSession) {
                    val resolvedSymbol =
                        callExpression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol
                    val contextArguments = resolvedSymbol?.contextArguments
                    contextArguments?.any { (it as? KaExplicitReceiverValue)?.expression == argumentExpression }
                }
            }

            if (contextArgument == true) {
                KotlinHighlightInfoTypeSemanticNames.CONTEXT_ARGUMENT
            } else {
                KotlinHighlightInfoTypeSemanticNames.NAMED_ARGUMENT
            }
        }

        highlightName(argument.project,
            argument,
            TextRange(argumentName.startOffset, eq.endOffset),
            infoType
        )
    }

    private fun visitPlainArgument(argument: KtValueArgument) {
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        val parent = argument.parent
        val range = TextRange(argumentName.startOffset, eq.endOffset)
        val type = if (parent is KtValueArgumentList && parent.parent is KtAnnotationEntry)
            KotlinHighlightInfoTypeSemanticNames.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
        else
            KotlinHighlightInfoTypeSemanticNames.NAMED_ARGUMENT

        highlightName(argument.project, argument, range, type)
    }
}

internal class ParameterNamesHighlightingExtension : KotlinAbstractSemanticHighlightingVisitor(), BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor =
        ParameterNamesHighlightingVisitor(holder)

    override fun createSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession): KtVisitorVoid =
        ParameterNamesHighlightingVisitor(holder, session)

    override fun clone(): ParameterNamesHighlightingExtension = ParameterNamesHighlightingExtension()
}