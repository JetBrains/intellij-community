// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.updates

import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.Compressor
import com.intellij.util.io.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
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

  @Test fun `executing MP commands only`() {
    val marketplaceSource = tempDir.newFileNio("marketplace-1.0.zip")
    Compressor.Zip(marketplaceSource).use { it.addFile("marketplace/content.txt", byteArrayOf()) }
    val marketplaceDestination = tempDir.newDirectoryPath("marketplace")
    val unpacked = marketplaceDestination.resolve("marketplace/content.txt")

    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.UnzipCommand(marketplaceSource, marketplaceDestination))
    StartupActionScriptManager.executeMarketplaceCommandsFromActionScript()

    assertThat(unpacked).exists()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `preserving non-MP commands`() {
    val regularFile = tempDir.newFileNio("temp.txt")
    assertThat(regularFile).exists()

    StartupActionScriptManager.addActionCommand(StartupActionScriptManager.DeleteCommand(regularFile))
    StartupActionScriptManager.executeMarketplaceCommandsFromActionScript()

    assertThat(regularFile).exists()
    assertThat(scriptFile).exists()
    assertThat(StartupActionScriptManager.loadActionScript(scriptFile)).hasSize(1)
  }

  @Test fun `executing mixed MP and non-MP commands`() {
    val pluginsDir = tempDir.newDirectoryPath("plugins")
    val downloadsDir = tempDir.newDirectoryPath("downloads")
    
    // Create plugins/marketplace directory with previous.txt
    val marketplaceDir = pluginsDir.resolve("marketplace")
    val previousFile = marketplaceDir.resolve("previous.txt")
    previousFile.parent.createDirectories()
    Files.writeString(previousFile, "previous content")
    
    // Create downloads/marketplace-1.0.zip with marketplace/new.txt
    val marketplaceZip = downloadsDir.resolve("marketplace-1.0.zip")
    marketplaceZip.parent.createDirectories()
    Compressor.Zip(marketplaceZip).use { it.addFile("marketplace/new.txt", "new content".toByteArray()) }
    
    // Create downloads/other-marketplace-1.0.zip with other/something.txt
    val otherMarketplaceZip = downloadsDir.resolve("other-marketplace-1.0.zip")
    Compressor.Zip(otherMarketplaceZip).use { it.addFile("other/something.txt", "something content".toByteArray()) }
    
    // Create downloads/other2-marketplace-2.0.jar with other2/test.txt
    val other2MarketplaceJar = downloadsDir.resolve("other2-marketplace-2.0.jar")
    Compressor.Zip(other2MarketplaceJar).use { it.addFile("other2/test.txt", "test content".toByteArray()) }
    
    // Create downloads/marketplace/plugin.zip with marketplace-plugin/secret.txt
    val marketplacePluginZip = downloadsDir.resolve("marketplace/plugin.zip")
    marketplacePluginZip.parent.createDirectories()
    Compressor.Zip(marketplacePluginZip).use { it.addFile("marketplace-plugin/secret.txt", "secret content".toByteArray()) }
    
    val otherDir = pluginsDir.resolve("other")
    val other2Dir = pluginsDir.resolve("other2")
    val marketplacePluginDir = pluginsDir.resolve("marketplace-plugin")
    
    // Setup commands
    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.DeleteCommand(marketplaceDir),
      StartupActionScriptManager.UnzipCommand(marketplaceZip, pluginsDir),
      StartupActionScriptManager.DeleteCommand(marketplaceZip),
      StartupActionScriptManager.DeleteCommand(otherDir),
      StartupActionScriptManager.UnzipCommand(otherMarketplaceZip, pluginsDir),
      StartupActionScriptManager.DeleteCommand(otherMarketplaceZip),
      StartupActionScriptManager.DeleteCommand(other2Dir),
      StartupActionScriptManager.CopyCommand(other2MarketplaceJar, pluginsDir),
      StartupActionScriptManager.DeleteCommand(other2MarketplaceJar),
      StartupActionScriptManager.DeleteCommand(marketplacePluginDir),
      StartupActionScriptManager.UnzipCommand(marketplacePluginZip, pluginsDir),
      StartupActionScriptManager.DeleteCommand(marketplacePluginZip)
    ))
    
    // Execute marketplace commands only
    StartupActionScriptManager.executeMarketplaceCommandsFromActionScript()
    
    // Verify only marketplace commands were executed
    val newFile = marketplaceDir.resolve("new.txt")
    assertThat(previousFile).doesNotExist()
    assertThat(newFile).exists()
    assertThat(marketplaceZip).exists() // source zip will be deleted after system dir lock
    
    // Verify other commands were NOT executed
    assertThat(otherDir).doesNotExist()
    assertThat(other2Dir).doesNotExist()
    assertThat(marketplacePluginDir).doesNotExist()
    assertThat(otherMarketplaceZip).exists()
    assertThat(other2MarketplaceJar).exists()
    assertThat(marketplacePluginZip).exists()
    
    // Verify remaining commands are still in script (10 non-marketplace commands)
    assertThat(scriptFile).exists()
    assertThat(StartupActionScriptManager.loadActionScript(scriptFile)).hasSize(10)
  }

  @Test fun `executing MP commands from empty script`() {
    assertThatCode { StartupActionScriptManager.executeMarketplaceCommandsFromActionScript() }.doesNotThrowAnyException()
    assertThat(scriptFile).doesNotExist()
  }

  @Test fun `updating MP plugin`() {
    val pluginsDir = tempDir.newDirectoryPath("plugins")
    val oldMarketplaceDir = pluginsDir.resolve("marketplace")
    val oldFile = oldMarketplaceDir.resolve("old-version.txt")
    oldFile.parent.createDirectories()
    Files.writeString(oldFile, "old content")

    val marketplaceZip = tempDir.newFileNio("marketplace-1.2.3.zip")
    Compressor.Zip(marketplaceZip).use { zip ->
      zip.addFile("marketplace/new-version.txt", "new content".toByteArray())
      zip.addFile("marketplace/plugin.xml", "<plugin></plugin>".toByteArray())
    }

    val newFile = oldMarketplaceDir.resolve("new-version.txt")
    val pluginXml = oldMarketplaceDir.resolve("plugin.xml")

    assertThat(oldFile).exists()
    assertThat(newFile).doesNotExist()
    assertThat(pluginXml).doesNotExist()
    assertThat(marketplaceZip).exists()

    StartupActionScriptManager.addActionCommands(listOf(
      StartupActionScriptManager.DeleteCommand(oldMarketplaceDir),
      StartupActionScriptManager.UnzipCommand(marketplaceZip, pluginsDir),
      StartupActionScriptManager.DeleteCommand(marketplaceZip)
    ))

    StartupActionScriptManager.executeMarketplaceCommandsFromActionScript()

    assertThat(oldFile).doesNotExist()
    assertThat(newFile).exists()
    assertThat(pluginXml).exists()
    assertThat(marketplaceZip).exists() // source zip will be deleted after system dir lock
    assertThat(scriptFile).exists()
  }

}
