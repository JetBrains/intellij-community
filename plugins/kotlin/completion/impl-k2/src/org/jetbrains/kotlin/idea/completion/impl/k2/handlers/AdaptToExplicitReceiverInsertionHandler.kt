// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferencesInRange
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.psi.KtFile


@Serializable
internal data class AdaptToExplicitReceiverInsertionHandler(
    val insertHandler: SerializableInsertHandler?,
    val receiverTextRangeStart: Int,
    val receiverTextRangeEnd: Int,
    val receiverText: String,
    val typeText: String,
): SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        // Insert type cast if the receiver type does not match.

        val explicitReceiverRange = context.document
            .createRangeMarker(receiverTextRangeStart, receiverTextRangeEnd)
        insertHandler?.handleInsert(context, item)

        val newReceiver = "(${receiverText} as $typeText)"
        context.document.replaceString(explicitReceiverRange.startOffset, explicitReceiverRange.endOffset, newReceiver)
        context.commitDocument()

        shortenReferencesInRange(
            file = context.file as KtFile,
            selection = explicitReceiverRange.textRange.grown(newReceiver.length),
        )
        context.doPostponedOperationsAndUnblockDocument()
    }
}