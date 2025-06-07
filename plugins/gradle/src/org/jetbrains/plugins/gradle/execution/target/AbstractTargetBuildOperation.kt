// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildAction
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation
import org.gradle.tooling.internal.consumer.BlockingResultHandler
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

internal abstract class AbstractTargetBuildOperation<This : AbstractTargetBuildOperation<This, R>, R>(
  private val connection: TargetProjectConnection,
  entryPoint: String
) : AbstractLongRunningOperation<This>(
  connection.parameters.connectionParameters
) {

  protected abstract val targetBuildParametersBuilder: TargetBuildParameters.Builder<*>

  protected open val buildActions: List<BuildAction<*>> = emptyList()

  protected open val prepareTaskState: Boolean = false

  init {
    operationParamsBuilder.setEntryPoint(entryPoint)
  }

  override fun addProgressListener(listener: ProgressListener, vararg operationTypes: OperationType): This {
    targetBuildParametersBuilder.withSubscriptions(operationTypes.asIterable())
    return super.addProgressListener(listener, *operationTypes)
  }

  override fun addProgressListener(listener: ProgressListener, eventTypes: MutableSet<OperationType>): This {
    targetBuildParametersBuilder.withSubscriptions(eventTypes)
    return super.addProgressListener(listener, eventTypes)
  }

  protected fun runAndGetResult(): R {
    val resultHandler = BlockingResultHandler(Any::class.java)
    runWithUncheckedHandler(resultHandler)
    @Suppress("UNCHECKED_CAST")
    return resultHandler.result as R
  }

  protected fun runWithHandler(handler: ResultHandler<in R>) {
    @Suppress("UNCHECKED_CAST")
    runWithUncheckedHandler(handler as ResultHandler<Any?>)
  }

  private fun runWithUncheckedHandler(handler: ResultHandler<Any?>) {
    val gradleHome = connection.distribution.gradleHome.maybeGetTargetValue()
    if (gradleHome != null) {
      targetBuildParametersBuilder.useInstallation(gradleHome)
    }
    val gradleUserHome = connection.parameters.gradleUserHome.maybeGetTargetValue()
    if (gradleUserHome != null) {
      targetBuildParametersBuilder.useGradleUserHome(gradleUserHome)
    }
    val classloaderHolder = GradleToolingProxyClassloaderHolder()
    for (buildAction in buildActions) {
      classloaderHolder.add(buildAction)
    }
    val serverRunner = GradleServerRunner(connection, consumerOperationParameters, prepareTaskState)
    serverRunner.run(classloaderHolder, targetBuildParametersBuilder, handler)
  }
}