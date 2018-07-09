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

  const val RESUME_LABEL: String = "idea.gui.test.resume.label"
  const val RESUME_TEST: String = "idea.gui.test.resume.testname"

  const val FILTER_KEY = "idea.gui.test.filter"
  private const val NO_NEED_TO_FILTER_TESTS: String = "NO_NEED_TO_FILTER_TESTS"

  fun getConfigPath(): String = getSystemProperty("idea.config.path", getConfigDefaultPath())
  fun getSystemPath(): String = getSystemProperty("idea.system.path", getSystemDefaultPath())
  fun isDebug(): Boolean = getSystemProperty("idea.debug.mode", false)
  fun suspendDebug(): String = if (isDebug()) "y" else "n"
  fun isInternal(): Boolean = getSystemProperty("idea.is.internal", true)
  fun useAppleScreenMenuBar(): Boolean = getSystemProperty("apple.laf.useScreenMenuBar", false)

  fun getDebugPort(): Int = getSystemProperty("idea.gui.test.debug.port", 5009)
  fun getBootClasspath(): String = getSystemProperty("idea.gui.test.bootclasspath", "../out/classes/production/intellij.platform.boot")
  fun getEncoding(): String = getSystemProperty("idea.gui.test.encoding", "UTF-8")
  fun getXmxSize(): Int = getSystemProperty("idea.gui.test.xmx", 512)
  //used for restarted and resumed test to qualify from what point to start
  fun getResumeInfo(): String = getSystemProperty(RESUME_LABEL, "DEFAULT")
  fun getResumeTestName(): String = getSystemProperty(RESUME_TEST, "undefined")


  fun shouldTestsBeFiltered(): Boolean = (getFilteredListOfTests() != NO_NEED_TO_FILTER_TESTS)
  //system property to set what tests should be run. -Didea.gui.test.filter=ShortClassName1,ShortClassName2
  fun getFilteredListOfTests(): String = getSystemProperty(FILTER_KEY, NO_NEED_TO_FILTER_TESTS)

  private fun getConfigDefaultPath(): String {
    return try {
      "${PathManager.getHomePath()}/config"
    }
    catch(e: RuntimeException) {
      "../config"
    }
  }

  private fun getSystemDefaultPath(): String {
    return try {
      "${PathManager.getHomePath()}/system"
    }
    catch(e: RuntimeException) {
      "../system"
    }
  }

  private inline fun <reified ReturnType> getSystemProperty(key: String, defaultValue: ReturnType): ReturnType {
    val value = System.getProperty(key) ?: return defaultValue
    return when (defaultValue) {
      is Int -> value.toInt() as ReturnType
      is Boolean -> value.toBoolean() as ReturnType
      is String -> value as ReturnType
      else -> throw Exception("Unable to get returning type of default value (not integer, boolean or string) for key: $key")
    }
  }
}