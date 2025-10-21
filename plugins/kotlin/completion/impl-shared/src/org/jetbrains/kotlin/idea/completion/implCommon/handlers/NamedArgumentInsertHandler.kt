/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.implCommon.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiWhiteSpace
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

@Serializable
data class NamedArgumentInsertHandler(
    @Serializable(with = KotlinNameSerializer::class) private val parameterName: Name
) : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val editor = context.editor

        val (textAfterCompletionArea, doNeedTrailingSpace) = context.file.findElementAt(context.tailOffset).let { psi ->
            psi?.siblings()?.firstOrNull { it !is PsiWhiteSpace }?.text to (psi !is PsiWhiteSpace)
        }

        var text: String
        var caretOffset: Int
        if (textAfterCompletionArea == "=") {
            // User tries to manually rename existing named argument. We shouldn't add trailing `=` in such case
            text = parameterName.render()
            caretOffset = text.length
        } else {
            // For complicated cases let's try to normalize the document firstly in order to avoid parsing errors due to incomplete code
            editor.document.replaceString(context.startOffset, context.tailOffset, "")
            PsiDocumentManager.getInstance(context.project).commitDocument(editor.document)

            val nextArgument = context.file.findElementAt(context.startOffset)?.siblings()
                ?.firstOrNull { it !is PsiWhiteSpace }?.parentsWithSelf?.takeWhile { it !is KtValueArgumentList }
                ?.firstIsInstanceOrNull<KtValueArgument>()

            if (nextArgument?.isNamed() == true) {
                if (doNeedTrailingSpace) {
                    text = "${parameterName.render()} = , "
                    caretOffset = text.length - 2
                } else {
                    text = "${parameterName.render()} = ,"
                    caretOffset = text.length - 1
                }
            } else {
                text = "${parameterName.render()} = "
                caretOffset = text.length
            }
        }

        if (context.file.findElementAt(context.startOffset - 1)?.let { it !is PsiWhiteSpace && it.text != "(" } == true) {
            text = " $text"
            caretOffset++
        }

        editor.document.replaceString(context.startOffset, context.tailOffset, text)
        editor.caretModel.moveToOffset(context.startOffset + caretOffset)
    }
}
