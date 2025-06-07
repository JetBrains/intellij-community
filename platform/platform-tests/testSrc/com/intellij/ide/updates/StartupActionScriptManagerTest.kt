// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.updates

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.TempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class StartupActionScriptManagerTest {
  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var scriptFile: Path

  @Before fun setUp() {
    scriptFile = PathManager.getStartupScriptDir().resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE)
    NioFiles.createDirectories(scriptFile.parent)
  }

  @After fun tearDown() {
    Files.deleteIfExists(scriptFile)
  }

  @Test fun `reading and writing empty file`() {
    StartupActionScriptManager.addActionCommands(listOf())
    assertThat(scriptFile).isRegularFile
    StartupActionScriptManager.executeActionScript()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'copy' command`() {
    val source = tempDir.newFile("source.txt").toPath()
    val destination = File(tempDir.root, "destination.txt").toPath()
    assertThat(source).exists()
    assertThat(destination).doesNotExist()
    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.CopyCommand(source, destination)))
    StartupActionScriptManager.executeActionScript()
    assertThat(destination).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'unzip' command`() {
    val source = IoTestUtil.createTestJar(tempDir.newFile("source.zip"), "zip/file.txt", "").toPath()
    val destination = tempDir.newDirectory("dir").toPath()
    val unpacked = destination.resolve("zip/file.txt")
    assertThat(source).exists()
    assertThat(unpacked).doesNotExist()
    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.UnzipCommand(source, destination)))
    StartupActionScriptManager.executeActionScript()
    assertThat(unpacked).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'delete' command`() {
    val tempFile = tempDir.newFile("temp.txt").toPath()
    assertThat(tempFile).exists()
    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.DeleteCommand(tempFile)))
    StartupActionScriptManager.executeActionScript()
    assertThat(tempFile).doesNotExist()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing commands with path mapping`() {
    val oldTarget = tempDir.newDirectory("old/plugins").toPath()
    val newTarget = tempDir.newDirectory("new/plugins").toPath()
    val copySource = tempDir.newFile("source.txt").toPath()
    val copyDestinationInOld = oldTarget.resolve("destination.txt")
    val copyDestinationInNew = newTarget.resolve("destination.txt")
    val unzipSource = IoTestUtil.createTestJar(tempDir.newFile("source.zip"), "zip/file.txt", "").toPath()
    val unpackedInOld = oldTarget.resolve("zip/file.txt")
    val unpackedInNew = newTarget.resolve("zip/file.txt")
    val deleteInOld = tempDir.newFile("old/plugins/to_delete.txt").toPath()
    val deleteInNew = tempDir.newFile("new/plugins/to_delete.txt").toPath()

    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.CopyCommand(copySource, copyDestinationInOld),
      StartupActionScriptManager.UnzipCommand(unzipSource, oldTarget),
      StartupActionScriptManager.DeleteCommand(deleteInOld)))
    val commands = StartupActionScriptManager.loadActionScript(scriptFile)
    StartupActionScriptManager.executeActionScriptCommands(commands, oldTarget, newTarget)

    assertThat(copyDestinationInOld).doesNotExist()
    assertThat(copyDestinationInNew).exists()
    assertThat(unpackedInOld).doesNotExist()
    assertThat(unpackedInNew).exists()
    assertThat(deleteInOld).exists()
    assertThat(deleteInNew).doesNotExist()
    assertThat(scriptFile).exists()
  }

  @Test fun `backward compatibility`() {
    val dataDir = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "updates/startupActionScript")
    Files.list(dataDir).use { it.toList() }.forEach { script ->
      val actions = StartupActionScriptManager.loadActionScript(script).map { it.toString() }
      assertThat(actions).describedAs("script: ${script.fileName}").containsExactly(
        "copy[/copy/src,/copy/dst]",
        "unzip[/unzip/src,/unzip/dst,null]",
        "unzip[/unzip/src,/unzip/dst,ImportSettingsFilenameFilter[f1,f2]]",
        "delete[/delete/src]")
    }
  }

  @Test fun `write error resilience`() {
    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.DeleteCommand(Path.of("file-1")),
      StartupActionScriptManager.DeleteCommand(Path.of("file-2")),
      StartupActionScriptManager.DeleteCommand(Path.of("file-3"))))

    val badCommands = listOf(
      StartupActionScriptManager.DeleteCommand(Path.of("file-4")),
      object : StartupActionScriptManager.ActionCommand { override fun execute(): Unit = throw UnsupportedOperationException() },
      StartupActionScriptManager.DeleteCommand(Path.of("file-5")))
    assertThatCode { StartupActionScriptManager.addActionCommands(badCommands) }.isInstanceOf(IOException::class.java)

    assertThat(scriptFile).exists()
    assertThat(StartupActionScriptManager.loadActionScript(scriptFile)).hasSize(3)
  }

  @Test fun `add action command to beginning`() {
    val source = tempDir.newFile("source.txt").toPath()
    val destination = File(tempDir.root, "destination.txt").toPath()
    assertThat(source).exists()
    assertThat(destination).doesNotExist()

    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.DeleteCommand(destination)))
    StartupActionScriptManager.addActionCommands(listOf(StartupActionScriptManager.CopyCommand(source, destination)))
    StartupActionScriptManager.addActionCommandsToBeginning(listOf(StartupActionScriptManager.DeleteCommand(destination)))

    StartupActionScriptManager.executeActionScript()
    assertThat(destination).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }
}
