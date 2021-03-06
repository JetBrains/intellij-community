// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironment.TargetPortBinding
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.util.text.nullize

class JavaGradleTargetEnvironmentAware : GradleTargetEnvironmentAware {
  override fun prepareTargetEnvironmentRequest(request: TargetEnvironmentRequest,
                                               environmentSetup: GradleServerEnvironmentSetup,
                                               progressIndicator: TargetProgressIndicator) {
    if (true) return

    val remotePort = 12345
    val remoteAddressForVmParams: String
    val java9plus: Boolean = environmentSetup.environmentConfiguration.runtimes
                               .findByType<JavaLanguageRuntimeConfiguration>()
                               ?.run { javaVersionString.nullize() }
                               ?.run { JavaSdkVersion.fromVersionString(this)?.isAtLeast(JavaSdkVersion.JDK_1_9) } ?: false

    remoteAddressForVmParams = if (java9plus) "*:$remotePort" else remotePort.toString()

    val javaParamsDelegate = object : JavaParameters() {
      override fun getClassPath() = environmentSetup.javaParameters.classPath
      override fun getVMParametersList(): ParametersList = environmentSetup.javaParameters.vmParametersList
      override fun getProgramParametersList(): ParametersList = environmentSetup.javaParameters.programParametersList
    }
    javaParamsDelegate.jdk = environmentSetup.javaParameters.jdk

    val remoteConnection: RemoteConnection = RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
      .suspend(true)
      .create(javaParamsDelegate)

    remoteConnection.applicationAddress = remotePort.toString()
    if (java9plus) {
      remoteConnection.applicationHostName = "*"
    }

    val targetPortBinding = TargetPortBinding(null, remotePort)
    request.targetPortBindings += targetPortBinding

    //environmentSetup.putUserData(SERVER_DEBUGGER_CONNECTION, DebuggerConnection(remoteConnection, targetPortBinding))
  }

  override fun handleCreatedTargetEnvironment(targetEnvironment: TargetEnvironment,
                                              environmentSetup: GradleServerEnvironmentSetup,
                                              targetProgressIndicator: TargetProgressIndicator) {
    //val debuggerConnection = SERVER_DEBUGGER_CONNECTION.get(environmentSetup) ?: return
    //debuggerConnection.resolveRemoteConnection(targetEnvironment)
  }

  private class DebuggerConnection(private val remoteConnection: RemoteConnection,
                                   val debuggerPortRequest: TargetPortBinding) {
    private var remoteConnectionResolved: Boolean = false

    fun resolveRemoteConnection(environment: TargetEnvironment) {
      val localPort = environment.targetPortBindings[debuggerPortRequest]
      remoteConnection.apply {
        debuggerHostName = "localhost"
        debuggerAddress = localPort.toString()
      }
      remoteConnectionResolved = true
    }
  }

  //companion object {
  //  private val SERVER_DEBUGGER_CONNECTION: Key<DebuggerConnection> = Key.create("Gradle server debugger connection")
  //}
}