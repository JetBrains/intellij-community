// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.upgradeBlocking
import com.intellij.platform.eel.where
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.nio.file.Path

@Service(Service.Level.APP)
internal class GitEelExecutableDetectionHelper private constructor(private val scope: CoroutineScope) {
  private val myCache = mutableMapOf<String, Deferred<String?>>()
  private val myLock = Any()

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getExecutablePathIfReady(eelApi: EelApi, rootDir: String): String? {
    return getExecutablePathPromise(eelApi, rootDir).takeIf { it.isCompleted }?.getCompleted()
  }

  fun getExecutablePathPromise(eelApi: EelApi, rootDir: String): Deferred<String?> {
    return synchronized(myLock) {
      myCache.computeIfAbsent(rootDir) {
        scope.async {
          eelApi.exec.where("git")?.toString()
        }
      }
    }
  }

  fun dropCache() {
    synchronized(myLock) {
      myCache.forEach { it.value.cancel() }
      myCache.clear()
    }
  }

  // TODO: use eel for local projects
  companion object {
    @JvmStatic
    fun getInstance() = application.service<GitEelExecutableDetectionHelper>()

    @JvmStatic
    fun canUseEel(): Boolean = Registry.`is`("git.use.eel.for.non.local.projects")

    @JvmStatic
    fun tryGetNonLocalEel(project: Project?, gitDirectory: Path?): EelApi? {
      val descriptor: EelDescriptor? = project?.getEelDescriptor() ?: gitDirectory?.getEelDescriptor()
      return descriptor?.takeIf { it !== LocalEelDescriptor }?.upgradeBlocking()
    }
  }
}