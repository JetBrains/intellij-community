// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString

class ScriptClassPathUtil {
    companion object {
        fun findVirtualFile(pathString: String): VirtualFile? {
            val path = pathString.toNioPathOrNull()
            return when {
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

class ScriptVirtualFileCache() {
    private val cache = mutableMapOf<String, Result<VirtualFile>>()

    fun findVirtualFile(pathString: String): VirtualFile? {
        val result = cache.computeIfAbsent(pathString) {
            val resultFile = ScriptClassPathUtil.findVirtualFile(pathString)
            resultFile?.let { Result.success(it) } ?: Result.failure(Throwable())
        }

        return result.getOrNull()
    }

    fun toVirtualFileUrl(pathString: String, project: Project): VirtualFileUrl {
        val url = when {
            pathString.endsWith(".jar") -> URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + pathString + URLUtil.JAR_SEPARATOR
            else -> URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtil.toSystemIndependentName(pathString)
        }

        return project.workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl(url)
    }
}
