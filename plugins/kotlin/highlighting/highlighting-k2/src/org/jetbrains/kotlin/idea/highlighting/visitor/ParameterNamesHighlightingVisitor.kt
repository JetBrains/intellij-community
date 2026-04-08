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
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
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
        val argumentName = argument.getArgumentName() ?: return
        val eq = argument.equalsToken ?: return
        val parent = argument.parent

        val infoType = if (parent is KtValueArgumentList && parent.parent is KtAnnotationEntry) {
            KotlinHighlightInfoTypeSemanticNames.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES
        } else {
            val callExpression = parent.parent as? KtCallExpression
            val argumentExpression = argument.getArgumentExpression()
            val kaSession = session

            val isContextArgument = when {
                callExpression == null -> {
                    false
                }

                !explicitContextArgumentsEnabled -> false

                kaSession == null -> {
                    // to cover explicit context arguments case we have to use resolve session,
                    // it is handled by another instance of ParameterNamesHighlightingVisitor
                    return
                }

                else -> {
                    context(kaSession) {
                        val resolveToCall = callExpression.resolveToCall()
                        val call =
                            when(resolveToCall) {
                                is KaSuccessCallInfo -> resolveToCall.call
                                is KaErrorCallInfo -> callExpression.resolveToCallCandidates().firstOrNull()?.candidate
                                else -> null
                            }
                        val callableMemberCall = call as? KaCallableMemberCall<*, *>
                        val contextParameterSymbols =
                            (callableMemberCall?.symbol as? KaFunctionSymbol)?.contextParameters ?: return
                        contextParameterSymbols.any { it.name == argumentName.asName }
                    }
                }
            }

            if (isContextArgument) {
                KotlinHighlightInfoTypeSemanticNames.CONTEXT_ARGUMENT
            } else {
                KotlinHighlightInfoTypeSemanticNames.NAMED_ARGUMENT
            }
        }

        val range = TextRange(argumentName.startOffset, eq.endOffset)

        highlightName(argument.project, argument, range, infoType)
    }
}

internal class ParameterNamesHighlightingExtension : KotlinAbstractSemanticHighlightingVisitor(), BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor =
        ParameterNamesHighlightingVisitor(holder)

    override fun createSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession): KtVisitorVoid =
        ParameterNamesHighlightingVisitor(holder, session)

    override fun clone(): ParameterNamesHighlightingExtension = ParameterNamesHighlightingExtension()
}