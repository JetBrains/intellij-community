// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.HashSetQueue
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager

@TestOnly
class TestingBackgroundExecutor internal constructor(
    private val manager: ScriptConfigurationManager
) : BackgroundExecutor {
    private val backgroundQueue = HashSetQueue<BackgroundTask>()

    private val rootsManager
        get() = manager.updater

    class BackgroundTask(val file: VirtualFile, val actions: () -> Unit) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BackgroundTask

            return file == other.file
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }
    }

    @Synchronized
    override fun ensureScheduled(key: VirtualFile, actions: () -> Unit) {
        backgroundQueue.add(BackgroundTask(key, actions))
    }

    fun doAllBackgroundTaskWith(actions: () -> Unit): Boolean {
        val copy = backgroundQueue.toList()
        backgroundQueue.clear()

        actions()

        rootsManager.update {
            copy.forEach {
                it.actions()
            }
        }

        ApplicationManager.getApplication().invokeLater {}
        UIUtil.dispatchAllInvocationEvents()

        return copy.isNotEmpty()
    }

    fun noBackgroundTasks(): Boolean = backgroundQueue.isEmpty()
}
