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

  val configPath: String
    get() = getSystemProperty("idea.config.path", configDefaultPath)
  val systemPath: String
    get() = getSystemProperty("idea.system.path", systemDefaultPath)
  val isDebug: Boolean
    get() = getSystemProperty("idea.debug.mode", false)
  val suspendDebug: String
    get() = if (isDebug) "y" else "n"
  val isInternal: Boolean
    get() = getSystemProperty("idea.is.internal", true)
  val useAppleScreenMenuBar: Boolean
    get() = getSystemProperty("apple.laf.useScreenMenuBar", false)
  val debugPort: Int
    get() = getSystemProperty("idea.gui.test.debug.port", 5009)
  val bootClasspath: String
    get() = getSystemProperty("idea.gui.test.bootclasspath", "../out/classes/production/intellij.platform.boot")
  val encoding: String
    get() = getSystemProperty("idea.gui.test.encoding", "UTF-8")
  val xmxSize: Int
    get() = getSystemProperty("idea.gui.test.xmx", 512)
  //used for restarted and resumed test to qualify from what point to start
  val resumeInfo: String
    get() = getSystemProperty(RESUME_LABEL, "DEFAULT")
  val resumeTestName: String
    get() = getSystemProperty(RESUME_TEST, "undefined")


  val shouldTestsBeFiltered: Boolean
    get() = (filteredListOfTests != NO_NEED_TO_FILTER_TESTS)
  //system property to set what tests should be run. -Didea.gui.test.filter=ShortClassName1,ShortClassName2
  val filteredListOfTests: String
    get() = getSystemProperty(FILTER_KEY, NO_NEED_TO_FILTER_TESTS)

  val screenRecorderJarDirPath: String?
    get() = System.getProperty("idea.gui.test.screenrecorder.jar.dir.path")
  val testsToRecord: List<String>
    get() = System.getProperty("idea.gui.test.screenrecorder.tests_to_record")?.split(";") ?: emptyList()
  val videoDuration: Long
    get() = getSystemProperty("idea.gui.test.screenrecorder.video_duration_in_minutes", 3)

  private val configDefaultPath: String
    get() {
      return try {
        "${PathManager.getHomePath()}/config"
      }
      catch (e: RuntimeException) {
        "../config"
      }
    }

  private val systemDefaultPath: String
    get() {
      return try {
        "${PathManager.getHomePath()}/system"
      }
      catch (e: RuntimeException) {
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