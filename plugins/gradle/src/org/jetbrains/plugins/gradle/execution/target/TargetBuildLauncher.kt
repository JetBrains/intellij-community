// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

internal class TargetBuildLauncher(
  connection: TargetProjectConnection
) : AbstractTargetBuildOperation<TargetBuildLauncher, Void>(connection, "TargetBuildLauncher API"),
    BuildLauncher {

  override val targetBuildParametersBuilder = TargetBuildParameters.BuildLauncherParametersBuilder()

  override val prepareTaskState: Boolean = true

  override fun run() {
    runAndGetResult()
  }

  override fun run(handler: ResultHandler<in Void>) {
    runWithHandler(handler)
  }

  override fun getThis(): TargetBuildLauncher = this

  override fun forTasks(vararg tasks: String): TargetBuildLauncher {
    operationParamsBuilder.setTasks(tasks.asList())
    return this
  }

  override fun forTasks(vararg tasks: Task): TargetBuildLauncher {
    return forTasks(tasks.asList())
  }

  override fun forTasks(tasks: Iterable<Task>): TargetBuildLauncher {
    return forLaunchables(tasks)
  }

  override fun forLaunchables(vararg launchables: Launchable): TargetBuildLauncher {
    return forLaunchables(launchables.asList())
  }

  override fun forLaunchables(launchables: Iterable<Launchable>): TargetBuildLauncher {
    operationParamsBuilder.setLaunchables(launchables)
    return this
  }
}
