// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_HOME_DIR
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.PINENTRY_LAUNCHER_FILE_NAME
import git4idea.config.GitExecutable
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<GpgAgentPathsLocator>()

internal interface GpgAgentPathsLocatorFactory {
  fun createPathLocator(project: Project, executor: GitExecutable): GpgAgentPathsLocator
}

internal class GpgAgentPathsLocatorFactoryImpl: GpgAgentPathsLocatorFactory {
  override fun createPathLocator(project: Project, executor: GitExecutable): GpgAgentPathsLocator {
    if (executor is GitExecutable.Wsl) {
      return WslGpgAgentPathsLocator(executor)
    }
    return MacAndUnixGpgAgentPathsLocator()
  }
}

internal interface GpgAgentPathsLocator {
  companion object {
    const val GPG_HOME_DIR = ".gnupg"
    const val GPG_AGENT_CONF_FILE_NAME = "gpg-agent.conf"
    const val GPG_AGENT_CONF_BACKUP_FILE_NAME = "gpg-agent.conf.bak"
    const val PINENTRY_LAUNCHER_FILE_NAME = "pinentry-ide.sh"
  }
  fun resolvePaths(): GpgAgentPaths?
}

private class MacAndUnixGpgAgentPathsLocator : GpgAgentPathsLocator {
  override fun resolvePaths(): GpgAgentPaths? {
    try {
      val gpgAgentHome = Paths.get(SystemProperties.getUserHome(), GPG_HOME_DIR)
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)

      return GpgAgentPaths.create(gpgAgentHome, gpgPinentryAppLauncher.toAbsolutePath().toString())
    }
    catch (e: InvalidPathException) {
      LOG.warn("Cannot resolve path", e)
      return null
    }
  }
}

private class WslGpgAgentPathsLocator(private val executable: GitExecutable.Wsl) : GpgAgentPathsLocator {
  override fun resolvePaths(): GpgAgentPaths? {
    try {
      val gpgAgentHome = getWindowsAccessibleGpgHome(executable) ?: return null
      val wslUserHome = getPathInWslUserHome(executable) ?: return null
      val pathToPinentryAppInWsl = "$wslUserHome/$GPG_HOME_DIR/$PINENTRY_LAUNCHER_FILE_NAME"
      return GpgAgentPaths.create(gpgAgentHome, pathToPinentryAppInWsl)
    }
    catch (e: InvalidPathException) {
      LOG.warn("Cannot resolve path", e)
      return null
    }
  }

  private fun getPathInWslUserHome(executable: GitExecutable.Wsl): String? {
    val wslDistribution = executable.distribution
    val wslUserHomePath = wslDistribution.userHome?.trimEnd('/')
    if (wslUserHomePath == null) return null
    LOG.debug("User home path in WSL = $wslUserHomePath")

    return wslUserHomePath
  }

  private fun getWindowsAccessibleGpgHome(executable: GitExecutable.Wsl): Path? {
    val wslDistribution = executable.distribution
    val wslUserHomePath = getPathInWslUserHome(executable)
    if (wslUserHomePath != null) {
      return Path.of(wslDistribution.getWindowsPath(wslUserHomePath), GPG_HOME_DIR)
    }
    else {
      LOG.warn("Cannot resolve wsl user home path")
    }
    return null
  }
}