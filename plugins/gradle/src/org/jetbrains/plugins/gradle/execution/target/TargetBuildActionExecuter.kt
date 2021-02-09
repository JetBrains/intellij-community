// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.ResultHandler
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.BuildActionParametersBuilder

internal class TargetBuildActionExecuter<T>(connection: TargetProjectConnection, buildAction: BuildAction<T?>) :
  TargetBuildExecuter<TargetBuildActionExecuter<T>, T?>(connection), BuildActionExecuter<T> {

  override val targetBuildParametersBuilder = BuildActionParametersBuilder<T?>(buildAction)
  override fun run(): T? = runAndGetResult()

  @Suppress("UNCHECKED_CAST")
  override fun run(handler: ResultHandler<in T>) = runWithHandler(handler as ResultHandler<Any?>)
  override fun getThis(): TargetBuildActionExecuter<T> = this
  override fun forTasks(vararg tasks: String): BuildActionExecuter<T> = apply { forTasks(tasks.asList()) }
  override fun forTasks(tasks: Iterable<String>): BuildActionExecuter<T> = apply { operationParamsBuilder.setTasks(tasks.toList()) }

  init {
    operationParamsBuilder.setEntryPoint("TargetBuildActionExecuter API")
  }
}