// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener
import org.jetbrains.kotlin.idea.gradleJava.scripting.legacy.GradleLegacyScriptListener
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootsManager

class GradleScriptListener(project: Project) : ScriptChangeListener(project) {
    // todo(gradle6): remove
    private val legacy = GradleLegacyScriptListener(project)

    private val buildRootsManager: GradleBuildRootsManager
        get() = GradleBuildRootsManager.getInstance(project) ?: error("GradleBuildRootsManager not found")

    init {
        // listen changes using VFS events, including gradle-configuration related files
        val listener = GradleScriptFileChangeListener(this, buildRootsManager)
        installAsyncVirtualFileListener(listener, KotlinPluginDisposable.getInstance(project))
    }

    // cache buildRootsManager service for hot path under vfs changes listener
    val fileChangesProcessor: (filePath: String, ts: Long) -> Unit
        get() {
            val buildRootsManager = buildRootsManager
            return { filePath, ts ->
                buildRootsManager.fileChanged(filePath, ts)
            }
        }

    fun fileChanged(filePath: String, ts: Long) =
        fileChangesProcessor(filePath, ts)

    override fun isApplicable(vFile: VirtualFile) =
        // todo(gradle6): replace with `isCustomScriptingSupport(vFile)`
        legacy.isApplicable(vFile)

    private fun isCustomScriptingSupport(vFile: VirtualFile) =
        buildRootsManager.isApplicable(vFile)

    override fun editorActivated(vFile: VirtualFile) {
        if (isCustomScriptingSupport(vFile)) {
            buildRootsManager.updateNotifications(restartAnalyzer = false) { it == vFile.path }
        } else {
            legacy.editorActivated(vFile)
        }
    }

    override fun documentChanged(vFile: VirtualFile) {
        fileChanged(vFile.path, System.currentTimeMillis())

        if (!isCustomScriptingSupport(vFile)) {
            legacy.documentChanged(vFile)
        }
    }
}

private class GradleScriptFileChangeListener(
    private val watcher: GradleScriptListener,
    private val buildRootsManager: GradleBuildRootsManager
) : VirtualFileChangesListener {

    val changedFiles = mutableListOf<String>()

    override fun isProcessRecursively() = true

    override fun init() {
        changedFiles.clear()
    }

    override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
        return buildRootsManager.maybeAffectedGradleProjectFile(file.path)
    }

    override fun updateFile(file: VirtualFile, event: VFileEvent) {
        changedFiles.add(event.path)
    }

    override fun apply() {
        val fileChangesProcessor = watcher.fileChangesProcessor
        changedFiles.forEach {
            LocalFileSystem.getInstance().findFileByPath(it)?.let { f ->
                watcher.fileChanged(f.path, f.timeStamp)
                fileChangesProcessor(f.path, f.timeStamp)
            }
        }
    }
}