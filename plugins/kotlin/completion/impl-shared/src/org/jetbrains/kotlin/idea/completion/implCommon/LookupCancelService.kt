// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus

class LookupCancelService {
    internal class Reminiscence(editor: Editor, offset: Int) {
        var editor: Editor? = editor
        private var marker: RangeMarker? = editor.document.createRangeMarker(offset, offset)

        // forget about auto-popup cancellation when the caret is moved to the start or before it
        private var editorListener: CaretListener? = object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                if (marker != null && (!marker!!.isValid || editor.logicalPositionToOffset(e.newPosition) <= offset)) {
                    dispose()
                }
            }
        }

        init {
            ThreadingAssertions.assertEventDispatchThread()
            editor.caretModel.addCaretListener(editorListener!!)
        }

        fun matches(editor: Editor, offset: Int): Boolean {
            return editor == this.editor && marker?.startOffset == offset
        }

        fun dispose() {
            ThreadingAssertions.assertEventDispatchThread()
            if (marker != null) {
                editor!!.caretModel.removeCaretListener(editorListener!!)
                marker = null
                editor = null
                editorListener = null
            }
        }
    }

    @ApiStatus.Internal
    val lookupCancelListener = object : LookupListener {
        override fun lookupCanceled(event: LookupEvent) {
            val lookup = event.lookup
            if (event.isCanceledExplicitly && lookup.isCompletion) {
                val offset = lookup.currentItem?.getUserData(AUTO_POPUP_AT)
                if (offset != null) {
                    lastReminiscence?.dispose()
                    if (offset <= lookup.editor.document.textLength) {
                        lastReminiscence = Reminiscence(lookup.editor, offset)
                    }
                }
            }
        }
    }

    @ApiStatus.Internal
    fun disposeLastReminiscence(editor: Editor) {
        if (lastReminiscence?.editor == editor) {
            lastReminiscence!!.dispose()
            lastReminiscence = null
        }
    }

    private var lastReminiscence: Reminiscence? = null

    companion object {
        fun getInstance(project: Project): LookupCancelService = project.service()

        fun getServiceIfCreated(project: Project): LookupCancelService? = project.getServiceIfCreated(LookupCancelService::class.java)

        val AUTO_POPUP_AT = Key<Int>("LookupCancelService.AUTO_POPUP_AT")
    }

    fun wasAutoPopupRecentlyCancelled(editor: Editor, offset: Int): Boolean {
        return lastReminiscence?.matches(editor, offset) ?: false
    }

}
