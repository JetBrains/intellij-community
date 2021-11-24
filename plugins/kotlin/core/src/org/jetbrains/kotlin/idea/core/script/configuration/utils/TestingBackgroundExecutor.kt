// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.configuration.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.HashSetQueue
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager

@TestOnly
class TestingBackgroundExecutor internal constructor(
    private val manager: CompositeScriptConfigurationManager
) : BackgroundExecutor {
    val rootsManager get() = manager.updater
    val backgroundQueue = HashSetQueue<BackgroundTask>()

    class BackgroundTask(val file: VirtualFile, val actions: () -> Unit) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BackgroundTask

            if (file != other.file) return false

            return true
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
