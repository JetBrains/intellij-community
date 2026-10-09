// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.sceneBuilder

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.function.Consumer
import java.util.function.Supplier

@Service(Service.Level.PROJECT)
internal class SceneBuilderBackgroundService(private val project: Project, private val coroutineScope: CoroutineScope) {

  fun <T : Any> computeInSmartMode(compute: Supplier<T>, onResult: Consumer<T>): Job {
    return coroutineScope.launch {
      val result = smartReadAction(project) { compute.get() }
      onResult.accept(result)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SceneBuilderBackgroundService = project.service()
  }
}
