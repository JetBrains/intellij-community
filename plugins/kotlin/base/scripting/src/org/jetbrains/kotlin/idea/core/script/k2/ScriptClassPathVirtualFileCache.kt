// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.core.script.scriptingWarnLog
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString

class ScriptClassPathVirtualFileCache {
    private constructor()

    private val cache = mutableMapOf<String, VirtualFile?>()

    fun findVirtualFile(pathString: String): VirtualFile? =
        cache.computeIfAbsent(pathString) {
            ScriptClassPathVirtualFileCache.findVirtualFile(pathString)
        }

    companion object {
        fun getInstance(): ScriptClassPathVirtualFileCache = ScriptClassPathVirtualFileCache()

        fun findVirtualFile(pathString: String): VirtualFile? {
            val path = pathString.toNioPathOrNull()
            val resultFile = when {
                path == null -> {
                    scriptingWarnLog("Invalid classpath entry '$pathString'")
                    null
                }

                path.notExists() -> {
                    scriptingWarnLog("Invalid classpath entry '$pathString', notExists=true")
                    null
                }

                path.isDirectory() -> {
                    StandardFileSystems.local()?.refreshIfNeededAndFindFileByPath(path.pathString)
                }

                path.isRegularFile() -> {
                    StandardFileSystems.jar()?.refreshIfNeededAndFindFileByPath(path.pathString + URLUtil.JAR_SEPARATOR)
                }

                else -> {
                    scriptingWarnLog("Invalid classpath entry '$pathString'")
                    null
                }
            }

            return resultFile
        }

        private fun VirtualFileSystem.refreshIfNeededAndFindFileByPath(path: String): VirtualFile? {
            val application = ApplicationManager.getApplication()

            findFileByPath(path)?.let { return it }

            //we cannot use `refreshAndFindFileByPath` under read lock
            if (application.isDispatchThread || !application.isReadAccessAllowed) {
                return refreshAndFindFileByPath(path)
            }

            return null
        }
    }
}
