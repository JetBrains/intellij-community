// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.execution.wsl.EelDescriptorWithWslDistribution
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.eel.where
import com.intellij.util.application
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import kotlin.io.path.pathString

private val LOG = logger<GitEelExecutableDetectionHelper>()

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.APP)
internal class GitEelExecutableDetectionHelper private constructor(private val scope: CoroutineScope) {
  private val myCache = mutableMapOf<String, Deferred<String?>>()
  private val myLock = Any()

  /**
   * Only peeks at the detection: it starts one if none is cached, but never starts a finished one again.
   * So it does not connect again to an environment that has just failed. Only [getExecutablePathPromise] does that.
   *
   * @return `null` if the detection is not finished yet, if it found no Git, or if it failed.
   */
  fun getExecutablePathIfReady(eelDescriptor: EelDescriptor, rootDir: String): String? {
    val promise = synchronized(myLock) {
      myCache.getOrPut(rootDir) { startDetection(eelDescriptor) }
    }
    return if (promise.isCompleted && promise.getCompletionExceptionOrNull() == null) promise.getCompleted() else null
  }

  /**
   * @return `null` if Git is not found, or if the environment is unavailable, for example after a lost SSH connection.
   * @see getExecutablePathPromise
   */
  fun getExecutablePathBlocking(eelDescriptor: EelDescriptor, rootDir: String): String? {
    return try {
      runBlockingMaybeCancellable {
        getExecutablePathPromise(eelDescriptor, rootDir).await()
      }
    }
    catch (e: EelUnavailableException) {
      LOG.info("Cannot detect Git in ${eelDescriptor.name}: ${e.message}")
      null
    }
  }

  /**
   * Gives the cached detection, or starts a new one if the cached one found no Git or failed.
   * A detection that failed with [EelUnavailableException.IntendedExit], for example because the user cancelled
   * the SSH authentication, stays cached until [dropCache]: a new attempt could ask the user again.
   */
  fun getExecutablePathPromise(eelDescriptor: EelDescriptor, rootDir: String): Deferred<String?> {
    return synchronized(myLock) {
      val existing = myCache[rootDir]
      if (existing != null && !needsNewDetection(existing)) {
        existing
      }
      else {
        startDetection(eelDescriptor).also {
          myCache[rootDir] = it
        }
      }
    }
  }

  private fun needsNewDetection(detection: Deferred<String?>): Boolean {
    if (!detection.isCompleted) return false
    return when (detection.getCompletionExceptionOrNull()) {
      null -> detection.getCompleted() == null
      is EelUnavailableException.IntendedExit -> false
      else -> true
    }
  }

  private fun startDetection(eelDescriptor: EelDescriptor): Deferred<String?> {
    return scope.async {
      eelDescriptor.toEelApi().exec.where("git")?.asNioPath()?.pathString
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

    /**
     * @return the Git executable in the Eel environment of [project] or [gitDirectory],
     * or `null` if Git commands for them do not run through Eel.
     * @see createEelExecutable
     */
    @JvmStatic
    fun tryGetEelExecutable(project: Project?, pathToGit: String, gitDirectory: Path?): GitExecutable? {
      return tryGetEelDescriptor(project, gitDirectory)?.let { createEelExecutable(it, pathToGit) }
    }

    /**
     * An unavailable environment, for example after a lost SSH connection, is not a bug. So it gives [GitExecutable.Unknown]:
     * a Git command with it fails with a normal error, and the next command connects again.
     */
    @VisibleForTesting
    fun createEelExecutable(descriptor: EelDescriptor, pathToGit: String): GitExecutable {
      val eel = try {
        descriptor.toEelApiBlocking()
      }
      catch (e: EelUnavailableException) {
        LOG.info("Cannot run Git in ${descriptor.name}: ${e.message}")
        return GitExecutable.Unknown(UNAVAILABLE_EEL_ID, pathToGit, GitBundle.message("git.executable.unknown.error.message", e.message))
      }
      val path = Path.of(pathToGit)
      return if (path.isAbsolute) GitExecutable.Eel(eel, path.asEelPath()) else GitExecutable.Eel(eel, pathToGit)
    }

    private const val UNAVAILABLE_EEL_ID = "eel-unavailable"

    /**
     * @return `true` if git commands for [project] (or [gitDirectory]) run through Eel on a machine other than
     * the one the IDE runs on, for example a Docker dev container. An application-wide setting, such as the
     * saved "Path to Git executable", names a path on the IDE's own machine, so it must not apply here.
     */
    @JvmStatic
    fun isNonLocalEelEnvironment(project: Project?, gitDirectory: Path?): Boolean {
      val descriptor = tryGetEelDescriptor(project, gitDirectory)
      return descriptor != null && descriptor !== LocalEelDescriptor
    }

    @JvmStatic
    fun tryGetEelDescriptor(project: Project?, gitDirectory: Path?): EelDescriptor? {
      val canUseEelForLocal = useEelForLocalProjects()
      val canUseEelForWsl = canUseEelForWsl()
      val canUseEelForOther = canUseEel()
      return if (!canUseEelForLocal && !canUseEelForOther && !canUseEelForWsl) {
        null
      } else {
        val descriptor: EelDescriptor? =
          project?.takeIf { !it.isDefault }?.getEelDescriptor() ?: gitDirectory?.getEelDescriptor()
        val shouldUse = when {
          descriptor === LocalEelDescriptor -> canUseEelForLocal
          descriptor is EelDescriptorWithWslDistribution -> canUseEelForWsl
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
