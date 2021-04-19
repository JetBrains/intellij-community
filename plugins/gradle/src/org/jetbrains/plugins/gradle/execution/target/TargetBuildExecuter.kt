// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import org.gradle.tooling.BuildAction
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation
import org.gradle.tooling.internal.consumer.BlockingResultHandler
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

internal abstract class TargetBuildExecuter<T : AbstractLongRunningOperation<T>, R : Any?>(private val connection: TargetProjectConnection) :
  AbstractLongRunningOperation<T>(connection.parameters.connectionParameters) {
  abstract val targetBuildParametersBuilder: TargetBuildParameters.Builder
  protected open val buildActions: List<BuildAction<*>> = emptyList()

  override fun addProgressListener(listener: ProgressListener?, vararg operationTypes: OperationType?): T {
    targetBuildParametersBuilder.withSubscriptions(operationTypes.asIterable().filterNotNull())
    return super.addProgressListener(listener, *operationTypes)
  }

  override fun addProgressListener(listener: ProgressListener?, eventTypes: MutableSet<OperationType>?): T {
    eventTypes?.let { targetBuildParametersBuilder.withSubscriptions(it) }
    return super.addProgressListener(listener, eventTypes)
  }

  protected fun runAndGetResult(): R = BlockingResultHandler(Any::class.java).run {
    runWithHandler(this)
    @Suppress("UNCHECKED_CAST")
    result as R
  }

  protected fun runWithHandler(handler: ResultHandler<Any?>) {
    val gradleHome = connection.distribution.gradleHome.maybeGetTargetValue()
    if (gradleHome != null) {
      targetBuildParametersBuilder.useInstallation(gradleHome)
    }
    val gradleUserHome = connection.parameters.gradleUserHome.maybeGetTargetValue()
    if (gradleUserHome != null) {
      targetBuildParametersBuilder.useGradleUserHome(gradleUserHome)
    }
    val classPathAssembler = GradleServerClasspathInferer()
    for (buildAction in buildActions) {
      classPathAssembler.add(buildAction)
    }
    GradleServerRunner(connection, consumerOperationParameters).run(classPathAssembler, targetBuildParametersBuilder, handler)
  }
}