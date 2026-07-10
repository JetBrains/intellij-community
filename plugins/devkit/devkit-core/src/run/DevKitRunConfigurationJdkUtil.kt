// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

internal fun getRunConfigurationModuleAndJdk(configuration: JavaRunConfigurationBase): Pair<Module, Sdk>? {
  return ReadAction.nonBlocking<Pair<Module, Sdk>?> {
    val module = configuration.configurationModule.module ?: return@nonBlocking null
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return@nonBlocking null
    module to jdk
  }.executeSynchronously()
}

@ApiStatus.Internal
fun usesJetBrainsRuntime(configuration: JavaRunConfigurationBase): Boolean {
  val (_, jdk) = getRunConfigurationModuleAndJdk(configuration) ?: return false
  return jdk.versionString?.contains("JetBrains Runtime") == true
}
