// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UiUtils")
package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

fun JTextComponent.onTextChange(action: (DocumentEvent) -> Unit) {
    document.addDocumentListener(
        object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                action(e)
            }
        }
    )
}

@ApiStatus.Internal
fun EditorNotificationPanel.createComponentActionLabel(@NlsContexts.LinkLabel labelText: String, callback: (HyperlinkLabel) -> Unit) {
    val label: Ref<HyperlinkLabel> = Ref.create()
    val action = Runnable {
        callback(label.get())
    }
    label.set(createActionLabel(labelText, action))
}