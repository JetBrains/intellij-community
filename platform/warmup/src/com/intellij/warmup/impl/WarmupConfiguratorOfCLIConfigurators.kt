// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.impl

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.CommandLineInspectionProjectAsyncConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.warmup.util.WarmupLogger
import java.nio.file.Path
import java.util.function.Predicate

/**
 * This class is a temporary bridge between old [CommandLineInspectionProjectConfigurator] and new [WarmupConfigurator].
 */
internal class WarmupConfiguratorOfCLIConfigurator(val delegate: CommandLineInspectionProjectConfigurator) : WarmupConfigurator {

  override suspend fun prepareEnvironment(projectPath: Path): Unit = reportRawProgress { reporter ->
    val context = produceConfigurationContext(reporter, projectPath, delegate.name)
    blockingContext {
      delegate.configureEnvironment(context)
    }
  }

  override suspend fun runWarmup(project: Project): Boolean = reportRawProgress { reporter ->
    val context = produceConfigurationContext(reporter, project.guessProjectDir()?.path?.let(Path::of), delegate.name)
    if (delegate is CommandLineInspectionProjectAsyncConfigurator) {
      delegate.configureProjectAsync(project, context)
      false
    }
    else {
      blockingContext {
        delegate.configureProject(project, context)
        false
      }
    }
  }

  override val configuratorPresentableName: String
    get() = delegate.name
}

internal fun getCommandLineReporter(sectionName: String): CommandLineInspectionProgressReporter = object : CommandLineInspectionProgressReporter {
  override fun reportError(message: String?) = message?.let { WarmupLogger.logInfo("[$sectionName]: $it") } ?: Unit

  override fun reportMessage(minVerboseLevel: Int, message: String?) = message?.let { WarmupLogger.logInfo("[$sectionName]: $it") } ?: Unit
}

private fun produceConfigurationContext(
  reporter: RawProgressReporter,
  projectDir: Path?,
  name: String,
): CommandLineInspectionProjectConfigurator.ConfiguratorContext {
  return object : CommandLineInspectionProjectConfigurator.ConfiguratorContext {
    val reporter = getCommandLineReporter(name)

    override fun getLogger(): CommandLineInspectionProgressReporter = this.reporter

    /**
     * Copy-pasted from [com.intellij.openapi.progress.RawProgressReporterIndicator]. ProgressIndicator will be deprecated,
     * so this code should not be here for long (famous last words...).
     */
    override fun getProgressIndicator(): ProgressIndicator = object : EmptyProgressIndicator() {
      override fun setText(text: String?) {
        reporter.text(text)
      }

      override fun setText2(text: String?) {
        reporter.details(text)
      }

      override fun setFraction(fraction: Double) {
        reporter.fraction(fraction)
      }

      override fun setIndeterminate(indeterminate: Boolean) {
        if (indeterminate) {
          reporter.fraction(null)
        }
        else {
          reporter.fraction(0.0)
        }
      }
    }

    override fun getProjectPath(): Path = projectDir ?: error("Something wrong with this project")

    override fun getFilesFilter(): Predicate<Path> = Predicate { true }

    override fun getVirtualFilesFilter(): Predicate<VirtualFile> = Predicate { true }
  }
}