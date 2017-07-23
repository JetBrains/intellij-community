/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.launcher

import com.intellij.openapi.application.PathManager

object GuiTestOptions {

  fun getConfigPath(): String = getSystemProperty("idea.config.path", getConfigDefaultPath())
  fun getSystemPath(): String = getSystemProperty("idea.system.path", getSystemDefaultPath())
  fun isDebug(): Boolean = getSystemProperty("idea.debug.mode", false)
  fun suspendDebug(): String = if (isDebug()) "y" else "n"
  fun isInternal(): Boolean = getSystemProperty("idea.is.internal", true)
  fun useAppleScreenMenuBar(): Boolean = getSystemProperty("apple.laf.useScreenMenuBar", false)

  fun getDebugPort(): Int = getSystemProperty("idea.gui.test.debug.port", 5009)
  fun getBootClasspath(): String = getSystemProperty("idea.gui.test.bootclasspath", "../out/classes/production/boot")
  fun getEncoding(): String = getSystemProperty("idea.gui.test.encoding", "UTF-8")
  fun getXmxSize(): Int = getSystemProperty("idea.gui.test.xmx", 512)

  fun getConfigDefaultPath(): String {
    try {
      return "${PathManager.getHomePath()}/config"
    }
    catch(e: RuntimeException) {
      return "../config"
    }
  }

  fun getSystemDefaultPath(): String {
    try {
      return "${PathManager.getHomePath()}/system"
    }
    catch(e: RuntimeException) {
      return "../system"
    }
  }

  inline fun <reified ReturnType> getSystemProperty(key: String, defaultValue: ReturnType): ReturnType {
    val value = System.getProperty(key) ?: return defaultValue
    return when (defaultValue) {
      is Int -> value.toInt() as ReturnType
      is Boolean -> value.toBoolean() as ReturnType
      is String -> value as ReturnType
      else -> throw Exception("Unable to get returning type of default value (not integer, boolean or string) for key: $key")
    }
  }
}