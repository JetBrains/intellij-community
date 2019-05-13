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
import com.intellij.openapi.util.io.FileUtil
import java.io.File

object GuiTestOptions {

  const val RESUME_LABEL: String = "idea.gui.test.resume.label"
  const val RESUME_TEST: String = "idea.gui.test.resume.testname"
  const val FILTER_KEY = "idea.gui.test.filter"

  private const val NO_NEED_TO_FILTER_TESTS: String = "NO_NEED_TO_FILTER_TESTS"

  val configPath: String by lazy { getSystemProperty("idea.config.path", configDefaultPath) }
  val systemPath: String by lazy { getSystemProperty("idea.system.path", systemDefaultPath) }
  val guiTestLogFile: String by lazy { javaClass.classLoader.getResource("gui-test-log.xml").file }
  val guiTestRootDirPath: String? by lazy { System.getProperty("idea.gui.tests.root.dir.path", null) }
  val isGradleRunner: Boolean by lazy { getSystemProperty("idea.gui.tests.gradle.runner", false) }

  val isDebug: Boolean by lazy { getSystemProperty("idea.debug.mode", false) }
  val isPassPrivacyPolicy: Boolean by lazy { getSystemProperty("idea.pass.privacy.policy", true) }
  val isPassDataSharing: Boolean by lazy { getSystemProperty("idea.pass.data.sharing", true) }
  val suspendDebug: String by lazy { if (isDebug) "y" else "n" }
  val isInternal: Boolean by lazy { getSystemProperty("idea.is.internal", true) }
  val useAppleScreenMenuBar: Boolean by lazy { getSystemProperty("apple.laf.useScreenMenuBar", false) }
  val debugPort: Int by lazy { getSystemProperty("idea.gui.test.debug.port", 5009) }
  val bootClasspath: String by lazy { getSystemProperty("idea.gui.test.bootclasspath", "../out/classes/production/intellij.platform.boot") }
  val encoding: String by lazy { getSystemProperty("idea.gui.test.encoding", "UTF-8") }
  val xmxSize: Int by lazy { getSystemProperty("idea.gui.test.xmx", 512) }

  //used for restarted and resumed test to qualify from what point to start
  val resumeInfo: String by lazy { getSystemProperty(RESUME_LABEL, "DEFAULT") }
  val resumeTestName: String by lazy { getSystemProperty(RESUME_TEST, "undefined") }

  val shouldTestsBeFiltered: Boolean by lazy { (filteredListOfTests != NO_NEED_TO_FILTER_TESTS) }
  //system property to set what tests should be run. -Didea.gui.test.filter=ShortClassName1,ShortClassName2
  val filteredListOfTests: String by lazy { getSystemProperty(FILTER_KEY, NO_NEED_TO_FILTER_TESTS) }

  val screenRecorderJarDirPath: String? by lazy { System.getenv("SCREENRECORDER_JAR_DIR") }
  val testsToRecord: List<String> by lazy { System.getenv("SCREENRECOREDER_TESTS_TO_RECORD")?.split(";") ?: emptyList() }
  val videoDuration: Long by lazy { System.getenv("SCREENRECORDER_VIDEO_DURATION")?.toLong() ?: 3 }

  // PyCharm Tests needs global projects folder
  val projectsDir: File by lazy {
    // The temporary location might contain symlinks, such as /var@ -> /private/var on MacOS.
    // EditorFixture seems to require a canonical path when opening the file.
    FileUtil.generateRandomTemporaryPath().canonicalFile
  }

  private val configDefaultPath: String by lazy {
    try {
      "${PathManager.getHomePath()}/config"
    }
    catch (e: RuntimeException) {
      "../config"
    }
  }

  private val systemDefaultPath: String by lazy {
    try {
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