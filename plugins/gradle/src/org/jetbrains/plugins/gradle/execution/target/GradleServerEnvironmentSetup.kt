// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GradleServerEnvironmentSetup : UserDataHolder {
  val javaParameters: SimpleJavaParameters
  val environmentConfiguration: TargetEnvironmentConfiguration

  companion object {
    val targetJavaExecutablePathMappingKey
      get() = "<<target java executable path>>"
  }
}