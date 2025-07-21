// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.k1.configuration.listener.ScriptChangeListener.Companion.LISTENER
import org.jetbrains.kotlin.idea.core.script.shared.isScriptChangesNotifierDisabled
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

internal class ScriptChangesNotifier(private val project: Project) {
    private val scriptsQueue: Alarm
    private val scriptChangesListenerDelayMillis = 1400

    init {
        val parentDisposable = KotlinPluginDisposable.getInstance(project)
        scriptsQueue = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

        project.messageBus.connect(parentDisposable).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    runScriptDependenciesUpdateIfNeeded(file)
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.newFile?.let { runScriptDependenciesUpdateIfNeeded(it) }
                }

                private fun runScriptDependenciesUpdateIfNeeded(file: VirtualFile) {
                    if (isUnitTestMode()) {
                        updateScriptDependenciesIfNeeded(file)
                    } else {
                        AppExecutorUtil.getAppExecutorService().submit {
                            updateScriptDependenciesIfNeeded(file)
                        }
                    }
                }
            },
        )

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val document = event.document
                    val file = FileDocumentManager.getInstance().getFile(document)?.takeIf { it.isInLocalFileSystem } ?: return

                    // Do not listen for changes in files that are not open
                    val editorManager = FileEditorManager.getInstance(project) ?: return
                    if (file !in editorManager.openFiles) {
                        return
                    }

                    if (isUnitTestMode()) {
                        getListener(project, file)?.documentChanged(file)
                    } else {
                        scriptsQueue.cancelAllRequests()
                        if (!project.isDisposed) {
                            scriptsQueue.addRequest(
                                { getListener(project, file)?.documentChanged(file) },
                                scriptChangesListenerDelayMillis,
                                true,
                            )
                        }
                    }
                }
            },
            parentDisposable
        )

        // Init project scripting idea EP listeners
        LISTENER.getExtensions(project)
    }

    private val defaultListener = DefaultScriptChangeListener(project)
    private val listeners: Collection<ScriptChangeListener>
        get() = mutableListOf<ScriptChangeListener>().apply {
            addAll(LISTENER.getExtensions(project))
            add(defaultListener)
        }

    fun updateScriptDependenciesIfNeeded(file: VirtualFile) {
        getListener(project, file)?.editorActivated(file)
    }

    private fun getListener(project: Project, file: VirtualFile): ScriptChangeListener? {
        if (project.isDisposed || areListenersDisabled()) return null

        return listeners.firstOrNull { it.isApplicable(file) }
    }

    private fun areListenersDisabled(): Boolean {
        return isUnitTestMode() && ApplicationManager.getApplication().isScriptChangesNotifierDisabled == true
    }
}
