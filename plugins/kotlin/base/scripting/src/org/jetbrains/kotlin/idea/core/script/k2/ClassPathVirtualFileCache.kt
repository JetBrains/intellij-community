// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.io.URLUtil
import org.jetbrains.kotlin.idea.core.script.scriptingWarnLog
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString

@Service(Service.Level.PROJECT)
class ClassPathVirtualFileCache {
    private val cache = ConcurrentHashMap<Path, Result<VirtualFile>>()

    fun get(pathString: String): VirtualFile? {
        val path = pathString.toNioPathOrNull() ?: return null

        val result = cache.computeIfAbsent(path) {
            val resultFile = when {
                path.notExists() -> {
                    scriptingWarnLog("Invalid classpath entry '$this', notExists=true")
                    null
                }
                path.isDirectory() -> {
                    StandardFileSystems.local()?.refreshIfNeededAndFindFileByPath(path.pathString)
                }
                path.isRegularFile() -> {
                    StandardFileSystems.jar()?.refreshIfNeededAndFindFileByPath(path.pathString + URLUtil.JAR_SEPARATOR)
                }
                else -> {
                    scriptingWarnLog("Invalid classpath entry '$this'")
                    null
                }
            }

            resultFile?.let { Result.success(it) } ?: Result.failure(Throwable())
        }

        return result.getOrNull()
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

    fun clear(): Unit = cache.clear()

    companion object {
        fun getInstance(project: Project): ClassPathVirtualFileCache = project.service<ClassPathVirtualFileCache>()
    }

}