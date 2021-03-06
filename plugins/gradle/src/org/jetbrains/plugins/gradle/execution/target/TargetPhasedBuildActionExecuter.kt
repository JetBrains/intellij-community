// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.PhasedBuildActionParametersBuilder

internal class TargetPhasedBuildActionExecuter(connection: TargetProjectConnection,
                                               projectsLoadedAction: PhasedBuildAction.BuildActionWrapper<Any>?,
                                               buildFinishedAction: PhasedBuildAction.BuildActionWrapper<Any>?) :
  TargetBuildExecuter<TargetPhasedBuildActionExecuter, Void>(connection),
  BuildActionExecuter<Void> {

  override val targetBuildParametersBuilder = PhasedBuildActionParametersBuilder(projectsLoadedAction?.action, buildFinishedAction?.action)
  override fun run(): Void = runAndGetResult()

  @Suppress("UNCHECKED_CAST")
  override fun run(handler: ResultHandler<in Void>) = runWithHandler(handler as ResultHandler<Any?>)
  override fun getThis() = this
  override fun forTasks(vararg tasks: String) = apply { forTasks(tasks.asList()) }
  override fun forTasks(tasks: Iterable<String>) = apply { operationParamsBuilder.setTasks(tasks.toList()) }

  init {
    operationParamsBuilder.setEntryPoint("TargetPhasedBuildActionExecuter API")
  }
}