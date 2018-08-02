// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.remote.transport

import java.io.Serializable

enum class RestartIdeCause { PLUGIN_INSTALLED, RUN_WITH_SYSTEM_PROPERTIES }
open class RestartIdeAndResumeContainer(val restartIdeCause: RestartIdeCause, val dataObject: Any? = null) : Serializable
data class RunWithSystemPropertiesContainer(val systemProperties: Array<Pair<String, String>>) : RestartIdeAndResumeContainer(
  RestartIdeCause.RUN_WITH_SYSTEM_PROPERTIES, systemProperties)