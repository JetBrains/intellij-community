// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("BuiltInServerConnectionData")
package org.jetbrains.idea.devkit.requestHandlers

import com.intellij.execution.configurations.JavaParameters
import com.intellij.openapi.project.Project
import org.jetbrains.ide.BuiltInServerManager

/**
 * Passes system properties which can be used to connect to the built-in server from a process started by the IDE.
 * Currently, it's used for 'intellij ultimate' project only.
 */
internal fun passDataAboutBuiltInServer(javaParameters: JavaParameters, project: Project) {
  val vmParameters = javaParameters.vmParametersList
  vmParameters.addProperty("intellij.dev.ide.server.port", BuiltInServerManager.getInstance().port.toString())
  vmParameters.addProperty("intellij.dev.project.location.hash", project.locationHash)
}