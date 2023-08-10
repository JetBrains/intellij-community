// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPortBinding
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus

/**
 * This extension allows to specify port at which Gradle server process will wait for debugger connections.
 */
@ApiStatus.Internal
class GradleServerDebugAware : GradleTargetEnvironmentAware {
  override fun prepareTargetEnvironmentRequest(request: TargetEnvironmentRequest,
                                               environmentSetup: GradleServerEnvironmentSetup,
                                               progressIndicator: TargetProgressIndicator) {
    val targetServerDebugPort = Registry.intValue("gradle.execution.target.server.debug.port", -1)
    if (targetServerDebugPort == -1) return

    val remoteAddressForVmParams: String
    val java9plus: Boolean = environmentSetup.environmentConfiguration.runtimes
                               .findByType<JavaLanguageRuntimeConfiguration>()
                               ?.run { javaVersionString.nullize() }
                               ?.run { JavaSdkVersion.fromVersionString(this)?.isAtLeast(JavaSdkVersion.JDK_1_9) } ?: false
    remoteAddressForVmParams = if (java9plus) "*:$targetServerDebugPort" else targetServerDebugPort.toString()
    val javaParamsDelegate = object : JavaParameters() {
      override fun getClassPath() = environmentSetup.javaParameters.classPath
      override fun getVMParametersList(): ParametersList = environmentSetup.javaParameters.vmParametersList
      override fun getProgramParametersList(): ParametersList = environmentSetup.javaParameters.programParametersList
    }
    javaParamsDelegate.jdk = environmentSetup.javaParameters.jdk
    val remoteConnection: RemoteConnection = RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
      .suspend(true)
      .create(javaParamsDelegate)
    remoteConnection.applicationAddress = targetServerDebugPort.toString()
    if (java9plus) {
      remoteConnection.applicationHostName = "*"
    }
    request.targetPortBindings += TargetPortBinding(null, targetServerDebugPort)
  }

  override fun handleCreatedTargetEnvironment(targetEnvironment: TargetEnvironment,
                                              environmentSetup: GradleServerEnvironmentSetup,
                                              targetProgressIndicator: TargetProgressIndicator) = Unit
}