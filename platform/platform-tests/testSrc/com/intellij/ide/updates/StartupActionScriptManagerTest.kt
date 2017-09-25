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
package com.intellij.ide.updates

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupActionScriptManagerTest : BareTestFixtureTestCase() {
  private lateinit var scriptFile: File

  @Before fun setUp() {
    scriptFile = File(PathManager.getPluginTempPath(), "action.script")
    scriptFile.parentFile.mkdirs()
  }

  @After fun tearDown() {
    FileUtil.delete(scriptFile)
  }

  @Test fun `reading and writing empty file`() {
    StartupActionScriptManager.addActionCommands(listOf())
    assertTrue(scriptFile.exists())
    StartupActionScriptManager.executeActionScript()
    assertFalse(scriptFile.exists())
  }

  @Test fun `reading empty file in old format`() {
    ObjectOutputStream(FileOutputStream(scriptFile, false)).use { it.writeObject(ArrayList<StartupActionScriptManager.ActionCommand>()) }
    assertTrue(scriptFile.exists())
    StartupActionScriptManager.executeActionScript()
    assertFalse(scriptFile.exists())
  }

  @Test fun `executing "delete" command`() {
    val tempFile = File.createTempFile("temp.", ".txt", scriptFile.parentFile)
    assertTrue(tempFile.exists())
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(tempFile))
    StartupActionScriptManager.executeActionScript()
    assertFalse(tempFile.exists())
  }
}