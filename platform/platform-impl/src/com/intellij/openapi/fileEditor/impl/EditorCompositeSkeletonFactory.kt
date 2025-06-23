// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * This service is responsible for creating and maintaining skeleton components for editor tabs
 *
 * Skeleton could be created in two cases:
 * 1. If the editor is not created in [SKELETON_DELAY] ms
 * 2. If the selected tab is changed from the tab with skeleton to the tab without a skeleton
 *
 * The first case is covered with [createSkeleton] method.
 * The second case is covered with [TabsListener] that listens for tab selection changes.
 */
@Service(Service.Level.PROJECT)
internal class EditorCompositeSkeletonFactory(private val project: Project, private val coroutineScope: CoroutineScope) {
  init {
    val fileEditorManager = FileEditorManager.getInstance(project)
    coroutineScope.launch {
      (fileEditorManager as? FileEditorManagerImpl)?.splitterFlow?.collectLatest {
        for (window in it.windows()) {
          window.tabbedPane.tabs.addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
              val newComposite = newSelection?.composite ?: return
              if (newComposite.isAvailable()) return // Skeleton is already removed

              val newCompositePanel = newComposite.component as EditorCompositePanel
              val oldComposite = oldSelection?.composite?.component as? EditorCompositePanel ?: return

              if (oldComposite.skeleton != null && newCompositePanel.skeleton == null) {
                newCompositePanel.setNewSkeleton(doCreateSkeleton(newCompositePanel.skeletonScope))
              }
            }
          }, project)
        }
      }
    }
  }


  private val currentlyShownSkeleton = AtomicInteger(0)
  private val initialTime: AtomicLong = AtomicLong(System.currentTimeMillis())

  companion object {
    fun getInstance(project: Project): EditorCompositeSkeletonFactory = project.service()
  }
  suspend fun createSkeleton(skeletonScope: CoroutineScope): EditorSkeleton {
    delay(SKELETON_DELAY)
    currentlyShownSkeleton.incrementAndGet()
    initialTime.compareAndSet(-1, System.currentTimeMillis())

    return doCreateSkeleton(skeletonScope)
  }

  private fun doCreateSkeleton(skeletonScope: CoroutineScope): EditorSkeleton {
    skeletonScope.awaitCancellationAndInvoke {
      // If the last skeleton is removed, the process of tab jumping is completed and the phase could be moved to show an initial animation
      if (currentlyShownSkeleton.decrementAndGet() == 0) {
        initialTime.set(-1)
      }
    }
    return EditorSkeleton(skeletonScope, initialTime)
  }
}


internal val SKELETON_DELAY: Long
  get() = Registry.intValue("editor.skeleton.delay.ms", 300).toLong()