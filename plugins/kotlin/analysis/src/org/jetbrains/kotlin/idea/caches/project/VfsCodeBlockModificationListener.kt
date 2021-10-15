// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.util.runInReadActionWithWriteActionPriority
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

class VfsCodeBlockModificationListener: StartupActivity.Background {
    override fun runActivity(project: Project) {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val kotlinOCBModificationListener = KotlinCodeBlockModificationListener.getInstance(project)
        val vfsEventsListener = AsyncVfsEventsListener { events: List<VFileEvent> ->
            val projectRelatedVfsFileChange = events.any { event ->
                val file = event.takeIf { it.isFromRefresh || it is VFileContentChangeEvent }?.file ?: return@any false
                runInReadActionWithWriteActionPriority {
                    ProjectRootsUtil.isProjectSourceFile(project, file)
                } ?: true
            }
            if (projectRelatedVfsFileChange) {
                kotlinOCBModificationListener.incModificationCount()
            }
        }
        if (isUnitTestMode()) {
            val connection = project.messageBus.connect(disposable)
            connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    vfsEventsListener.filesChanged(events)
                }
            })
        } else {
            AsyncVfsEventsPostProcessor.getInstance().addListener(vfsEventsListener, disposable)
        }
    }
}