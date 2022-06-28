// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.kotlin.idea.configuration.notifications.showEapAdvertisementNotification
import org.jetbrains.kotlin.idea.util.isKotlinFileType

class KotlinTypingListenerService : Disposable {
    init {
        val application = ApplicationManager.getApplication()
        if (!application.isUnitTestMode && !application.isHeadlessEnvironment) {
            val eventMulticaster = EditorFactory.getInstance().eventMulticaster
            val documentListener: DocumentListener = object : DocumentListener {
                override fun documentChanged(e: DocumentEvent) {
                    FileDocumentManager.getInstance().getFile(e.document)?.let { virtualFile ->
                        if (virtualFile.isKotlinFileType() && virtualFile.isInLocalFileSystem) {
                            KotlinPluginUpdater.getInstance().kotlinFileEdited()
                            showEapAdvertisementNotification()
                        }
                    }
                }
            }

            eventMulticaster.addDocumentListener(documentListener, this)
        }
    }

    override fun dispose() = Unit
}