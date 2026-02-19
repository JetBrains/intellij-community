// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * This service is responsible for creating and maintaining skeleton components for editor tabs
 *
 * Skeleton could be created in two cases:
 * 1. If the editor is not created in [SKELETON_DELAY] ms
 * 2. If the new tab (without an editor) is shown after a recently shown skeleton
 */
@Service(Service.Level.PROJECT)
internal class EditorCompositeSkeletonFactory(project: Project, private val scope: CoroutineScope) {
  private val currentlyShownSkeleton = AtomicInteger(0)
  private val initialTime: AtomicLong = AtomicLong(System.currentTimeMillis())

  companion object {
    fun getInstance(project: Project): EditorCompositeSkeletonFactory = project.service()
  }
  suspend fun createSkeleton(skeletonScope: CoroutineScope): EditorSkeleton? {
    if (!Registry.`is`("editor.skeleton.animation.enabled")) return null
    // if [currentlyShownSkeleton] equals 0, then there's no skeleton shown at the moment. We need to delay the skeleton creation to avoid flickering
    if (currentlyShownSkeleton.get() == 0) {
      delay(SKELETON_DELAY)
    }

    currentlyShownSkeleton.incrementAndGet()
    initialTime.compareAndSet(-1, System.currentTimeMillis())
    return doCreateSkeleton(skeletonScope)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun doCreateSkeleton(skeletonScope: CoroutineScope): EditorSkeleton {
    skeletonScope.awaitCancellationAndInvoke {
      scope.launch {
        // delay actual deletion for skeleton to avoid flickering
        delay(SKELETON_DELAY)
        // If the last skeleton is removed, the process of tab jumping is completed and the phase could be moved to show an initial animation
        if (currentlyShownSkeleton.decrementAndGet() == 0) {
          initialTime.set(-1)
        }
      }
    }
    return EditorSkeleton(skeletonScope, initialTime)
  }
}


internal val SKELETON_DELAY: Long
  get() = Registry.intValue("editor.skeleton.delay.ms", 300).toLong()