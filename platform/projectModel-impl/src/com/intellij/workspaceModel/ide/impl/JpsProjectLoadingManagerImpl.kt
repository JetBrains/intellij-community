// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean

class JpsProjectLoadingManagerImpl : JpsProjectLoadingManager {
  @TestOnly
  fun isProjectLoaded(): Boolean = projectLoaded.get()

  private val projectLoaded = AtomicBoolean(false)
  private val projectActivities = ContainerUtil.createConcurrentList<Runnable>()

  @Synchronized
  override fun jpsProjectLoaded(action: Runnable) {
    if (projectLoaded.get()) {
      action.run()
    }
    else {
      projectActivities.add(action)
    }
  }

  @Synchronized
  fun loaded() {
    projectLoaded.set(true)
    projectActivities.forEach(Runnable::run)
  }
}

private class JpsProjectLoadedListenerImpl(private val project: Project) : JpsProjectLoadedListener {
  override fun loaded() {
    val manager = JpsProjectLoadingManager.getInstance(project) as JpsProjectLoadingManagerImpl
    manager.loaded()
  }
}
