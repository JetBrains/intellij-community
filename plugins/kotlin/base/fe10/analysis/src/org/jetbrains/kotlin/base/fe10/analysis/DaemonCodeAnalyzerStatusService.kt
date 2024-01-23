// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class DaemonCodeAnalyzerStatusService(project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): DaemonCodeAnalyzerStatusService = project.service()
    }

    @Volatile
    var daemonRunning: Boolean = false
        private set

    init {
        val messageBusConnection = project.messageBus.connect(this)
        messageBusConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
            override fun daemonStarting(fileEditors: Collection<FileEditor>) {
                daemonRunning = true
            }

            override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                daemonRunning = false
            }

            override fun daemonCancelEventOccurred(reason: String) {
                daemonRunning = false
            }
        })
    }

    override fun dispose() {}
}