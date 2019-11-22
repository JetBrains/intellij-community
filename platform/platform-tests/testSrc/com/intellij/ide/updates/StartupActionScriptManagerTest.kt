// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.updates

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.outputStream
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.ObjectOutputStream
import java.nio.file.Path
import java.nio.file.Paths

class StartupActionScriptManagerTest : BareTestFixtureTestCase() {
  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var scriptFile: Path

  @Before fun setUp() {
    scriptFile = Paths.get(PathManager.getPluginTempPath(), StartupActionScriptManager.ACTION_SCRIPT_FILE)
    scriptFile.parent.createDirectories()
  }

  @After fun tearDown() {
    scriptFile.delete()
  }

  @Test fun `reading and writing empty file`() {
    StartupActionScriptManager.addActionCommands(listOf())
    assertTrue(scriptFile.exists())
    StartupActionScriptManager.executeActionScript()
    assertFalse(scriptFile.exists())
  }

  @Test fun `reading empty file in old format`() {
    ObjectOutputStream(scriptFile.outputStream()).use { it.writeObject(ArrayList<StartupActionScriptManager.ActionCommand>()) }
    assertTrue(scriptFile.exists())
    StartupActionScriptManager.executeActionScript()
    assertFalse(scriptFile.exists())
  }

  @Test fun `executing "copy" command`() {
    val source = tempDir.newFile("source.txt")
    val destination = File(tempDir.root, "destination.txt")
    assertTrue(source.exists())
    assertFalse(destination.exists())
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.CopyCommand(source, destination))
    StartupActionScriptManager.executeActionScript()
    assertTrue(destination.exists())
    assertTrue(source.exists())
    assertFalse(scriptFile.exists())
  }

  @Test fun `executing "unzip" command`() {
    val source = IoTestUtil.createTestJar(tempDir.newFile("source.zip"), "zip/file.txt", "")
    val destination = tempDir.newFolder("dir")
    val unpacked = File(destination, "zip/file.txt")
    assertTrue(source.exists())
    assertFalse(unpacked.exists())
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.UnzipCommand(source, destination))
    StartupActionScriptManager.executeActionScript()
    assertTrue(unpacked.exists())
    assertTrue(source.exists())
    assertFalse(scriptFile.exists())
  }

  @Test fun `executing "delete" command`() {
    val tempFile = tempDir.newFile("temp.txt")
    assertTrue(tempFile.exists())
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(tempFile))
    StartupActionScriptManager.executeActionScript()
    assertFalse(tempFile.exists())
    assertFalse(scriptFile.exists())
  }

  @Test fun `executing commands with path mapping`() {
    val oldTarget = tempDir.newFolder("old/plugins")
    val newTarget = tempDir.newFolder("new/plugins")
    val copySource = tempDir.newFile("source.txt")
    val copyDestinationInOld = File(oldTarget, "destination.txt")
    val copyDestinationInNew = File(newTarget, "destination.txt")
    val unzipSource = IoTestUtil.createTestJar(tempDir.newFile("source.zip"), "zip/file.txt", "")
    val unpackedInOld = File(oldTarget, "zip/file.txt")
    val unpackedInNew = File(newTarget, "zip/file.txt")
    val deleteInOld = tempDir.newFile("old/plugins/to_delete.txt")
    val deleteInNew = tempDir.newFile("new/plugins/to_delete.txt")

    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.CopyCommand(copySource, copyDestinationInOld),
      StartupActionScriptManager.UnzipCommand(unzipSource, oldTarget),
      StartupActionScriptManager.DeleteCommand(deleteInOld)))
    StartupActionScriptManager.executeActionScript(scriptFile.toFile(), oldTarget, newTarget)

    assertFalse(copyDestinationInOld.exists())
    assertTrue(copyDestinationInNew.exists())
    assertFalse(unpackedInOld.exists())
    assertTrue(unpackedInNew.exists())
    assertTrue(deleteInOld.exists())
    assertFalse(deleteInNew.exists())
    assertTrue(scriptFile.exists())
  }
}