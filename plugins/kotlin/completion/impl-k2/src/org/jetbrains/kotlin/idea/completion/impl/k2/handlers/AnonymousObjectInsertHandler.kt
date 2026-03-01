// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersHandler
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.psi.KtFile

@Serializable
internal class AnonymousObjectInsertHandler(
    val constructorParenthesis: String,
    val renderedClassifier: String,
    val renderedTypeArgs: String?,
    val hasTypeArguments: Boolean,
) : SerializableInsertHandler  {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        val startOffset = context.startOffset
        val ktFile = context.file as? KtFile ?: return

        val settings = ktFile.kotlinCustomSettings
        val spaceBefore = if (settings.SPACE_BEFORE_EXTEND_COLON) " " else ""
        val spaceAfter = if (settings.SPACE_AFTER_EXTEND_COLON) " " else ""

        val objectBody = "object$spaceBefore:$spaceAfter$renderedClassifier"

        val text = buildString {
            append(objectBody)

            if (renderedTypeArgs != null) {
                append(renderedTypeArgs)
            } else if (hasTypeArguments) {
                append("<>")
            }
            append("$constructorParenthesis {}")
        }

        context.document.replaceString(startOffset, context.tailOffset, text)
        PsiDocumentManager.getInstance(context.project).commitDocument(context.document)

        if (!hasTypeArguments || renderedTypeArgs != null) {
            context.editor.caretModel.moveToOffset(startOffset + text.length - 1)
            ShortenReferencesFacility.getInstance().shorten(ktFile, TextRange(startOffset, startOffset + text.length))

            @OptIn(KaAllowAnalysisFromWriteAction::class, KaAllowAnalysisOnEdt::class)
            allowAnalysisFromWriteAction {
                allowAnalysisOnEdt {
                    KtImplementMembersHandler().invoke(context.project, context.editor, context.file, true)
                }
            }
        } else {
            context.editor.caretModel.moveToOffset(startOffset + objectBody.length + 1) // put caret into "<>"
            ShortenReferencesFacility.getInstance().shorten(ktFile, TextRange(startOffset, startOffset + text.length))
        }
    }
}
