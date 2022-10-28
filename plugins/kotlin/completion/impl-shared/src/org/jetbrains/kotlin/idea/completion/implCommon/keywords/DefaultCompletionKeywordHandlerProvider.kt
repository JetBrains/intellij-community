// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.kotlin.idea.completion.handlers.createKeywordConstructLookupElement
import org.jetbrains.kotlin.idea.completion.handlers.withLineIndentAdjuster
import org.jetbrains.kotlin.idea.completion.implCommon.keywords.BreakContinueKeywordHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf

object DefaultCompletionKeywordHandlerProvider : CompletionKeywordHandlerProvider<CompletionKeywordHandler.NO_CONTEXT>() {
    private val CONTRACT_HANDLER = completionKeywordHandler<CompletionKeywordHandler.NO_CONTEXT>(KtTokens.CONTRACT_KEYWORD) { _, _, _, _ ->
        emptyList()
    }

    private val GETTER_HANDLER =
        completionKeywordHandler<CompletionKeywordHandler.NO_CONTEXT>(KtTokens.GET_KEYWORD) { parameters, _, lookupElement, project ->
            buildList {
                add(lookupElement.withLineIndentAdjuster())
                if (!parameters.isUseSiteAnnotationTarget) {
                    add(
                        createKeywordConstructLookupElement(
                            project,
                            KtTokens.GET_KEYWORD.value,
                            "val v:Int get()=caret",
                            adjustLineIndent = true,
                        )
                    )
                    add(
                        createKeywordConstructLookupElement(
                            project,
                            KtTokens.GET_KEYWORD.value,
                            "val v:Int get(){caret}",
                            trimSpacesAroundCaret = true,
                            adjustLineIndent = true,
                        )
                    )
                }
            }
        }

    private val SETTER_HANDLER =
        completionKeywordHandler<CompletionKeywordHandler.NO_CONTEXT>(KtTokens.SET_KEYWORD) { parameters, _, lookupElement, project ->
            buildList {
                add(lookupElement.withLineIndentAdjuster())
                if (!parameters.isUseSiteAnnotationTarget) {
                    add(
                        createKeywordConstructLookupElement(
                            project,
                            KtTokens.SET_KEYWORD.value,
                            "var v:Int set(value)=caret",
                            adjustLineIndent = true,
                        )
                    )

                    add(
                        createKeywordConstructLookupElement(
                            project,
                            KtTokens.SET_KEYWORD.value,
                            "var v:Int set(value){caret}",
                            trimSpacesAroundCaret = true,
                            adjustLineIndent = true,
                        )
                    )
                }
            }
        }

    override val handlers = CompletionKeywordHandlers(
        BreakContinueKeywordHandler(KtTokens.BREAK_KEYWORD),
        BreakContinueKeywordHandler(KtTokens.CONTINUE_KEYWORD),
        GETTER_HANDLER, SETTER_HANDLER,
        CONTRACT_HANDLER,
    )
}

private val CompletionParameters.isUseSiteAnnotationTarget
    get() = position.prevLeaf()?.node?.elementType == KtTokens.AT