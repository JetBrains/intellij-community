// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.handlers.isTextAt
import org.jetbrains.kotlin.renderer.render

@Serializable
@Polymorphic
internal open class QuotedNamesAwareInsertionHandler : SerializableInsertHandler {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val lookupElement = item.`object` as KotlinLookupObject

        val startOffset = context.startOffset
        if (startOffset > 0 && context.document.isTextAt(startOffset - 1, "`")) {
            context.document.deleteString(startOffset - 1, startOffset)
        }
        context.document.replaceString(context.startOffset, context.tailOffset, lookupElement.shortName.render())

        context.commitDocument()
    }
}