// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.TargetValue
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetIntermediateResultHandler

@ApiStatus.Internal
interface GradleServerEnvironmentSetup {

  fun prepareEnvironment(
    targetBuildParametersBuilder: TargetBuildParameters.Builder<*>,
    consumerOperationParameters: ConsumerOperationParameters,
    progressIndicator: GradleServerProgressIndicator,
  ): TargetedCommandLine

  fun getJavaParameters(): SimpleJavaParameters

  fun getEnvironmentConfiguration(): TargetEnvironmentConfiguration

  fun getTargetEnvironment(): TargetEnvironment

  fun getTargetIntermediateResultHandler(): TargetIntermediateResultHandler

  fun getTargetBuildParameters(): TargetBuildParameters

  fun getProjectUploadRoot(): TargetEnvironment.UploadRoot

  fun getServerBindingPort(): TargetValue<Int>?

  companion object {
    val targetJavaExecutablePathMappingKey
      get() = "<<target java executable path>>"
  }
}