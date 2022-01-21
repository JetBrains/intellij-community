// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import java.util.concurrent.TimeUnit

/**
 * Highlighting daemon is restarted when exception is thrown, if there is a recurred error
 * (e.g. within a resolve) it could lead to infinite highlighting loop.
 *
 * Do not rethrow exception too often to disable HL for a while
 */
class KotlinHighlightingSuspender(private val project: Project) {
    private val timeoutSeconds = Registry.intValue("kotlin.suspended.highlighting.timeout", 10)

    private val lastThrownExceptionTimestampPerFile = mutableMapOf<VirtualFile, Long>()
    private val suspendTimeoutMs = TimeUnit.SECONDS.toMillis(timeoutSeconds.toLong())

    /**
     * @return true, when file is suspended for the 1st time (within a timeout window)
     */
    fun suspend(file: VirtualFile): Boolean {
        cleanup()

        if (suspendTimeoutMs <= 0) return false

        val timestamp = System.currentTimeMillis()
        // daemon is restarted when exception is thrown
        // if there is a recurred error (e.g. within a resolve) it could lead to infinite highlighting loop
        // so, do not rethrow exception too often to disable HL for a while
        val lastThrownExceptionTimestamp = synchronized(lastThrownExceptionTimestampPerFile) {
            val lastThrownExceptionTimestamp = lastThrownExceptionTimestampPerFile[file] ?: run {
                lastThrownExceptionTimestampPerFile[file] = timestamp
                0L
            }
            lastThrownExceptionTimestamp
        }

        updateNotifications(file)

        return timestamp - lastThrownExceptionTimestamp > suspendTimeoutMs
    }

    fun isSuspended(file: VirtualFile): Boolean {
        cleanup()

        val timestamp = System.currentTimeMillis()
        val lastThrownExceptionTimestamp = synchronized(lastThrownExceptionTimestampPerFile) {
            lastThrownExceptionTimestampPerFile[file] ?: return false
        }

        return timestamp - lastThrownExceptionTimestamp < suspendTimeoutMs
    }

    private fun cleanup() {
        val timestamp = System.currentTimeMillis()

        val filesToUpdate = mutableListOf<VirtualFile>()
        synchronized(lastThrownExceptionTimestampPerFile) {
            val it = lastThrownExceptionTimestampPerFile.entries.iterator()
            while (it.hasNext()) {
                val next = it.next()
                if (timestamp - next.value > suspendTimeoutMs) {
                    filesToUpdate += next.key
                    it.remove()
                }
            }
        }

        filesToUpdate.forEach { updateNotifications(it) }
    }

    private fun updateNotifications(file: VirtualFile) {
        EditorNotifications.getInstance(project).updateNotifications(file)
    }

    companion object {
        fun getInstance(project: Project): KotlinHighlightingSuspender = project.service()
    }
}