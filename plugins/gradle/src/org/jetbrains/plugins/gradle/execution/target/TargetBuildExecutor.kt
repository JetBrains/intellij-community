// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.BuildActionParametersBuilder
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.PhasedBuildActionParametersBuilder
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.StreamedValueAwareBuilder

internal abstract class TargetBuildExecutor<R>(
  connection: TargetProjectConnection,
  entryPoint: String
) : AbstractTargetBuildOperation<TargetBuildExecutor<R>, R>(connection, entryPoint),
    BuildActionExecuter<R> {

  abstract override val targetBuildParametersBuilder: StreamedValueAwareBuilder<*>

  override fun run(): R {
    return runAndGetResult()
  }

  override fun run(handler: ResultHandler<in R>) {
    runWithHandler(handler)
  }

  override fun getThis(): TargetBuildExecutor<R> = this

  override fun forTasks(vararg tasks: String): TargetBuildExecutor<R> {
    return forTasks(tasks.asList())
  }

  override fun forTasks(tasks: Iterable<String>): TargetBuildExecutor<R> {
    operationParamsBuilder.setTasks(tasks.toList())
    return this
  }

  override fun setStreamedValueListener(listener: StreamedValueListener) {
    targetBuildParametersBuilder.setStreamedValueListener(listener)
  }

  companion object {

    fun <R> createDefaultExecutor(
      connection: TargetProjectConnection,
      buildAction: BuildAction<R>
    ): TargetBuildExecutor<R> {
      return object : TargetBuildExecutor<R>(connection, "TargetBuildActionExecutor API") {

        override val targetBuildParametersBuilder = BuildActionParametersBuilder(buildAction)

        override val buildActions = listOf(buildAction)
      }
    }

    fun createPhasedExecutor(
      connection: TargetProjectConnection,
      projectsLoadedAction: PhasedBuildAction.BuildActionWrapper<Any>?,
      buildFinishedAction: PhasedBuildAction.BuildActionWrapper<Any>?
    ): TargetBuildExecutor<Void> {
      return object : TargetBuildExecutor<Void>(connection, "TargetPhasedBuildActionExecutor API") {

        override val targetBuildParametersBuilder = PhasedBuildActionParametersBuilder(projectsLoadedAction, buildFinishedAction)

        override val buildActions = listOfNotNull(projectsLoadedAction?.action, buildFinishedAction?.action)
      }
    }
  }
}