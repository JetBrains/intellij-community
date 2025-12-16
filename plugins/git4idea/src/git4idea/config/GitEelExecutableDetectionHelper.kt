// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.eel.where
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.nio.file.Path
import kotlin.io.path.pathString

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.APP)
internal class GitEelExecutableDetectionHelper private constructor(private val scope: CoroutineScope) {
  private val myCache = mutableMapOf<String, Deferred<String?>>()
  private val myLock = Any()

  fun getExecutablePathIfReady(eelDescriptor: EelDescriptor, rootDir: String): String? {
    return getExecutablePathPromise(eelDescriptor, rootDir).takeIf { it.isCompleted }?.getCompleted()
  }

  fun getExecutablePathBlocking(eelDescriptor: EelDescriptor, rootDir: String): String? {
    return runBlockingMaybeCancellable {
      getExecutablePathPromise(eelDescriptor, rootDir).await();
    }
  }

  fun getExecutablePathPromise(eelDescriptor: EelDescriptor, rootDir: String): Deferred<String?> {
    return synchronized(myLock) {
      val existing = myCache[rootDir]
      if (existing != null && (!existing.isCompleted || existing.getCompleted() != null)) {
        existing
      }
      else {
        scope.async {
          eelDescriptor.toEelApi().exec.where("git")?.asNioPath()?.pathString
        }.also {
          myCache[rootDir] = it
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

  companion object {
    @JvmStatic
    fun getInstance() = application.service<GitEelExecutableDetectionHelper>()

    @JvmStatic
    fun canUseEel(): Boolean = Registry.`is`("git.use.eel.for.container.projects")

    @JvmStatic
    fun canUseEelForWsl(): Boolean = Registry.`is`("git.use.eel.for.wsl.projects")

    @JvmStatic
    fun useEelForLocalProjects(): Boolean = Registry.`is`("git.use.eel.for.local.projects")

    @JvmStatic
    fun tryGetEel(project: Project?, gitDirectory: Path?): EelApi? {
      return tryGetEelDescriptor(project, gitDirectory)?.toEelApiBlocking()
    }

    @JvmStatic
    fun tryGetEelDescriptor(project: Project?, gitDirectory: Path?): EelDescriptor? {
      val canUseEelForLocal = useEelForLocalProjects()
      val canUseEelForWsl = canUseEelForWsl()
      val canUseEelForOther = canUseEel()
      return if (!canUseEelForLocal && !canUseEelForOther && !canUseEelForWsl) {
        null
      } else {
        val descriptor: EelDescriptor? = project?.getEelDescriptor() ?: gitDirectory?.getEelDescriptor()
        val shouldUse = when {
          descriptor === LocalEelDescriptor -> canUseEelForLocal
          descriptor is WslEelDescriptor -> canUseEelForWsl
          else -> canUseEelForOther
        }
        if (!shouldUse) {
          null
        } else {
          descriptor
        }
      }
    }
  }
}