// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Application-wide registry of weakly-held [Project] and [Editor] instances, populated from their lifecycle
 * events by [LeakCandidateEditorListener] and [LeakCandidateProjectListener].
 *
 * Its sole purpose is to let [ProjectLeakDetector.detect] cheaply decide whether the expensive object-graph walk
 * is worth running: after a GC, every instance that is *not* leaked has been reclaimed and dropped from these weak
 * sets, so if no surviving instance is disposed there is nothing to find and the walk can be skipped.
 */
@ApiStatus.Internal
@Service
class LeakCandidateRegistry {
  private val lock = Any()
  private val projects: MutableSet<Project> = ContainerUtil.createWeakSet()
  private val editors: MutableSet<Editor> = ContainerUtil.createWeakSet()

  fun register(project: Project): Unit = synchronized(lock) { projects.add(project) }
  fun register(editor: Editor): Unit = synchronized(lock) { editors.add(editor) }

  fun hasRetainedDisposedInstances(): Boolean = synchronized(lock) {
    projects.any { it.isDisposed } || editors.any { it.isDisposed }
  }

  companion object {
    fun getInstance(): LeakCandidateRegistry = service()
  }
}

internal class LeakCandidateEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    if (!ApplicationManager.getApplication().isInternal) return
    LeakCandidateRegistry.getInstance().register(event.editor)
  }
}

internal class LeakCandidateProjectListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!ApplicationManager.getApplication().isInternal) return
    LeakCandidateRegistry.getInstance().register(project)
  }
}
