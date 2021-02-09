// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ResultHandler
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

internal class TargetModelBuilder<T>(connection: TargetProjectConnection, modelType: Class<T>) :
  TargetBuildExecuter<TargetModelBuilder<T>, T?>(connection), ModelBuilder<T> {

  override val targetBuildParametersBuilder = TargetBuildParameters.ModelBuilderParametersBuilder(modelType)

  override fun get(): T? = runAndGetResult()

  @Suppress("UNCHECKED_CAST")
  override fun get(handler: ResultHandler<in T>) = runWithHandler(handler as ResultHandler<Any?>)
  override fun getThis(): TargetModelBuilder<T> = this
  override fun forTasks(vararg tasks: String): TargetModelBuilder<T> = apply { forTasks(tasks.asList()) }
  override fun forTasks(tasks: Iterable<String>): TargetModelBuilder<T> = apply { operationParamsBuilder.setTasks(tasks.toList()) }

  init {
    operationParamsBuilder.setEntryPoint("TargetModelBuilder API")
  }
}