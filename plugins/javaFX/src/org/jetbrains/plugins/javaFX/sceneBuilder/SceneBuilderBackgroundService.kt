// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.sceneBuilder

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.net.URLClassLoader
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SceneBuilderBackgroundService(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  private var loader: URLClassLoader? = null

  fun <T : Any> computeInSmartMode(compute: Supplier<T>, onResult: Consumer<T>, onError: Consumer<Throwable>): Job =
    coroutineScope.launch {
      try {
        onResult.accept(smartReadAction(project) { compute.get() })
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        onError.accept(e)
      }
    }

  @Synchronized
  fun getOrCreateLoader(factory: Supplier<URLClassLoader>): URLClassLoader =
    loader ?: factory.get().also { loader = it }

  @Synchronized
  fun resetLoader() {
    loader = null
  }

  @Synchronized
  override fun dispose() {
    runCatching { loader?.close() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SceneBuilderBackgroundService = project.service()
  }
}
