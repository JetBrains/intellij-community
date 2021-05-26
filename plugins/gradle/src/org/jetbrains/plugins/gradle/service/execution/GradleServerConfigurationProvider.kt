// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GradleServerConfigurationProvider : TargetEnvironmentConfigurationProvider {
  fun getServerBindingAddress(targetEnvironmentConfiguration: TargetEnvironmentConfiguration): HostPort? = null
  fun getClientCommunicationAddress(targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
                                    gradleServerHostPort: HostPort): HostPort? = null
}