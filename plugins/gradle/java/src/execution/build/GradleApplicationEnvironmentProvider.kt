// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.task.ExecuteRunConfigurationTask
import org.jetbrains.plugins.gradle.service.execution.loadApplicationInitScript

/**
 * @author Vladislav.Soroka
 */
open class GradleApplicationEnvironmentProvider : GradleBaseApplicationEnvironmentProvider<ApplicationConfiguration>() {

  override fun isApplicable(task: ExecuteRunConfigurationTask): Boolean {
    return task.runProfile.javaClass == ApplicationConfiguration::class.java
  }

  override fun generateInitScript(params: GradleInitScriptParameters): String? {
    val shortenCommandLine = params.configuration.shortenCommandLine
    val useManifestJar = shortenCommandLine === ShortenCommandLine.MANIFEST
    val useArgsFile = shortenCommandLine === ShortenCommandLine.ARGS_FILE
    var useClasspathFile = shortenCommandLine === ShortenCommandLine.CLASSPATH_FILE
    var intelliJRtPath: String? = null
    if (useClasspathFile) {
      try {
        intelliJRtPath = FileUtil.toCanonicalPath(
          PathManager.getJarPathForClass(Class.forName("com.intellij.rt.execution.CommandLineWrapper")))
      }
      catch (t: Throwable) {
        LOG.warn("Unable to use classpath file", t)
        useClasspathFile = false
      }
    }
    return loadApplicationInitScript(
      gradlePath = params.gradleTaskPath,
      runAppTaskName = params.runAppTaskName,
      mainClassToRun = params.mainClass,
      javaExePath = params.javaExePath,
      sourceSetName = params.sourceSetName,
      params = params.params,
      definitions = params.definitions,
      intelliJRtPath = intelliJRtPath,
      workingDirectory = params.workingDirectory,
      useManifestJar = useManifestJar,
      useArgsFile = useArgsFile,
      useClasspathFile = useClasspathFile,
      javaModuleName = params.javaModuleName
    )
  }

  companion object {
    private val LOG = logger<GradleApplicationEnvironmentProvider>()
  }
}
