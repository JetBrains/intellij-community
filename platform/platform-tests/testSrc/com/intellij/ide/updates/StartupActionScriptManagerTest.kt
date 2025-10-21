// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.updates

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.Compressor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries

class StartupActionScriptManagerTest {
  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var scriptFile: Path

  @Before fun setUp() {
    scriptFile = PathManager.getStartupScriptDir()
      .createDirectories()
      .resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE)
  }

  @After fun tearDown() {
    scriptFile.deleteIfExists()
  }

  @Test fun `reading and writing empty file`() {
    StartupActionScriptManager.addActionCommands(listOf())
    assertThat(scriptFile).isRegularFile
    StartupActionScriptManager.executeActionScript()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'copy' command`() {
    val source = tempDir.newFileNio("source.txt")
    val destination = source.resolveSibling("destination.txt")
    assertThat(source).exists()
    assertThat(destination).doesNotExist()
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.CopyCommand(source, destination))
    StartupActionScriptManager.executeActionScript()
    assertThat(destination).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'unzip' command`() {
    val source = tempDir.newFileNio("source.zip")
    Compressor.Zip(source).use { it.addFile("zip/file.txt", byteArrayOf()) }
    val destination = tempDir.newDirectoryPath("dir")
    val unpacked = destination.resolve("zip/file.txt")
    assertThat(source).exists()
    assertThat(unpacked).doesNotExist()
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.UnzipCommand(source, destination))
    StartupActionScriptManager.executeActionScript()
    assertThat(unpacked).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing 'delete' command`() {
    val tempFile = tempDir.newFileNio("temp.txt")
    assertThat(tempFile).exists()
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(tempFile))
    StartupActionScriptManager.executeActionScript()
    assertThat(tempFile).doesNotExist()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `executing commands with path mapping`() {
    val oldTarget = tempDir.newDirectoryPath("old/plugins")
    val newTarget = tempDir.newDirectoryPath("new/plugins")
    val copySource = tempDir.newFileNio("source.txt")
    val copyDestinationInOld = oldTarget.resolve("destination.txt")
    val copyDestinationInNew = newTarget.resolve("destination.txt")
    val unzipSource = tempDir.newFileNio("source.zip")
    Compressor.Zip(unzipSource).use { it.addFile("zip/file.txt", byteArrayOf()) }
    val unpackedInOld = oldTarget.resolve("zip/file.txt")
    val unpackedInNew = newTarget.resolve("zip/file.txt")
    val deleteInOld = tempDir.newFileNio("old/plugins/to_delete.txt")
    val deleteInNew = tempDir.newFileNio("new/plugins/to_delete.txt")

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
    dataDir.listDirectoryEntries().forEach { script ->
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
      StartupActionScriptManager.ActionCommand { throw UnsupportedOperationException() },
      StartupActionScriptManager.DeleteCommand(Path.of("file-5")))
    assertThatCode { StartupActionScriptManager.addActionCommands(badCommands) }.isInstanceOf(IOException::class.java)

    assertThat(scriptFile).exists()
    assertThat(StartupActionScriptManager.loadActionScript(scriptFile)).hasSize(3)
  }

  @Test fun `add action command to beginning`() {
    val source = tempDir.newFileNio("source.txt")
    val destination = source.resolveSibling("destination.txt")
    assertThat(source).exists()
    assertThat(destination).doesNotExist()

    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(destination))
    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.CopyCommand(source, destination))
    StartupActionScriptManager.addActionCommandsToBeginning(listOf(StartupActionScriptManager.DeleteCommand(destination)))

    StartupActionScriptManager.executeActionScript()
    assertThat(destination).exists()
    assertThat(source).exists()
    assertThat(scriptFile).doesNotExist()
  }
}
