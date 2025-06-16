// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.util.Key
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import java.nio.file.Path

internal class TargetModelBuilder<T>(
  private val connection: TargetProjectConnection,
  private val modelType: Class<T>
) : AbstractTargetBuildOperation<TargetModelBuilder<T>, T>(connection, "TargetModelBuilder API"),
    ModelBuilder<T> {

  override val targetBuildParametersBuilder = TargetBuildParameters.ModelBuilderParametersBuilder(modelType)

  override fun get(): T? = when (modelType) {
    BuildEnvironment::class.java -> {
      val gradleConnectionException = connection.getUserData(BUILD_ENVIRONMENT_REQUEST_FAILURE_KEY)
      if (gradleConnectionException != null) throw gradleConnectionException
      try {
        val buildEnvironment = connection.getUserData(BUILD_ENVIRONMENT_KEY) ?: runAndGetResult()
        connection.putUserData(BUILD_ENVIRONMENT_KEY, buildEnvironment as BuildEnvironment?)
        @Suppress("UNCHECKED_CAST")
        buildEnvironment as T?
      } catch (e: GradleConnectionException) {
        connection.putUserData(BUILD_ENVIRONMENT_REQUEST_FAILURE_KEY, e)
        throw e
      }
    }
    else -> {
      runAndGetResult()
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun get(handler: ResultHandler<in T>) = when (modelType) {
    BuildEnvironment::class.java -> {
      val buildEnvironment = connection.getUserData(BUILD_ENVIRONMENT_KEY)
      if (buildEnvironment != null) {
        handler.onComplete(buildEnvironment as T)
      }
      else {
        val gradleConnectionException = connection.getUserData(BUILD_ENVIRONMENT_REQUEST_FAILURE_KEY)
        if (gradleConnectionException != null) {
          handler.onFailure(gradleConnectionException)
        }
        else {
          runWithHandler(object : ResultHandler<Any?> {
            override fun onComplete(result: Any?) {
              connection.putUserData(BUILD_ENVIRONMENT_KEY, result as BuildEnvironment?)
              handler.onComplete(result as T?)
            }

            override fun onFailure(e: GradleConnectionException) {
              connection.putUserData(BUILD_ENVIRONMENT_REQUEST_FAILURE_KEY, e)
              handler.onFailure(e)
            }
          })
        }
      }
    }
    else -> runWithHandler(handler as ResultHandler<Any?>)
  }

  override fun getThis(): TargetModelBuilder<T> = this

  override fun forTasks(vararg tasks: String): TargetModelBuilder<T> {
    return forTasks(tasks.asList())
  }

  override fun forTasks(tasks: Iterable<String>): TargetModelBuilder<T> {
    operationParamsBuilder.setTasks(tasks.toList())
    return this
  }

  companion object {
    private val BUILD_ENVIRONMENT_KEY = Key.create<BuildEnvironment>("build environment model")
    private val BUILD_ENVIRONMENT_REQUEST_FAILURE_KEY = Key.create<GradleConnectionException>("build environment model request failure")
  }

  /*
    We have to set the value as without the check.
    It's impossible to set the value with the setter due to the forced `ConsumerOperationParameters.validateJavaHome(javaHome)` invocation.
    The path would always be invalid if we're dealing with the remote File System due to `java.io.File`'s limitations.

    It couldn't be solved by delegating, due to the overall structure of the BuildParameters because it strictly relies on
    internal and package private classes such as org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters.
*/
  @ApiStatus.Internal
  fun patchJavaHome(javaHome: Path) {
    val builderField = AbstractLongRunningOperation::class.java.getDeclaredField("operationParamsBuilder")
    val javaHomeField = builderField.type.getDeclaredField("javaHome")
    javaHomeField.setAccessible(true)
    javaHomeField.set(builderField.get(this), javaHome.toFile())
  }
}