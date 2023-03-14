// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.impl

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.environment.impl.produceConfigurationContext
import com.intellij.ide.warmup.WarmupConfigurator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.nio.file.Path

/**
 * This class is a temporary bridge between old [CommandLineInspectionProjectConfigurator] and new [WarmupConfigurator].
 */
internal class WarmupConfiguratorOfCLIConfigurator(val delegate: CommandLineInspectionProjectConfigurator) : WarmupConfigurator {

  override suspend fun prepareEnvironment(projectPath: Path) =
    withRawProgressReporter {
      val context = produceConfigurationContext(projectPath)
      blockingContext {
        delegate.configureEnvironment(context)
      }
    }

  override suspend fun runWarmup(project: Project): Boolean =
    withRawProgressReporter {
      val context = produceConfigurationContext(project.guessProjectDir()?.path?.let(Path::of))
      blockingContext {
        delegate.configureProject(project, context)
        false
      }
    }

  override val configuratorPresentableName: String
    get() = delegate.name
}

private val LOG: Logger = logger<WarmupConfigurator>()