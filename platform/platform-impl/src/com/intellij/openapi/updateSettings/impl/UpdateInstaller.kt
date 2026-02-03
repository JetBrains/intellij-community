// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.DynamicBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.currentJavaVersion
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.swing.UIManager

@Suppress("UseOptimizedEelFunctions")
@ApiStatus.Internal
object UpdateInstaller {
  const val UPDATER_MAIN_CLASS: String = "com.intellij.updater.Runner"

  private val LOG = logger<UpdateInstaller>()

  private const val PATCH_FILE_NAME = "patch-file.zip"
  private const val UPDATER_ENTRY = "com/intellij/updater/Runner.class"

  @JvmStatic
  @Throws(IOException::class)
  fun downloadPatchChain(chain: List<BuildNumber>, indicator: ProgressIndicator): List<Path> {
    indicator.text = IdeBundle.message("update.downloading.patch.progress")

    val files = mutableListOf<Path>()
    val share = 1.0 / (chain.size - 1)

    for (i in 1 until chain.size) {
      val from = chain[i - 1]
      val to = chain[i]
      val patchFile = getTempDir().resolve("patch-${from.withoutProductCode().asString()}-${to.withoutProductCode().asString()}.jar")
      val url = ExternalProductResourceUrls.getInstance().computePatchUrl(from, to)
                ?: error("Metadata contains information about patch '${from}' -> '${to}', but 'computePatchUrl' returns 'null'")
      val partIndicator = @Suppress("UsagesOfObsoleteApi") object : DelegatingProgressIndicator(indicator) {
        override fun setFraction(fraction: Double) = super.setFraction((i - 1) * share + fraction / share)
      }
      LOG.info("downloading ${url}")
      HttpRequests.request(url).gzip(false).saveToFile(patchFile, partIndicator)
      try {
        ZipFile(@Suppress("IO_FILE_USAGE") patchFile.toFile()).use {
          if (it.getEntry(PATCH_FILE_NAME) == null || it.getEntry(UPDATER_ENTRY) == null) {
            throw IOException("Corrupted patch file: ${patchFile}")
          }
        }
      }
      catch (e: ZipException) {
        throw IOException("Corrupted patch file: ${patchFile}", e)
      }
      files.add(patchFile)
    }

    return files
  }

  @JvmStatic
  @RequiresBackgroundThread
  fun downloadPluginUpdates(downloaders: Collection<PluginDownloader>, indicator: ProgressIndicator): List<PluginDownloader> {
    indicator.text = IdeBundle.message("update.downloading.plugins.progress")

    val updateChecker = UpdateCheckerFacade.getInstance()
    updateChecker.saveDisabledToUpdatePlugins()

    val disabledToUpdate = updateChecker.disabledToUpdate
    val readyToInstall = mutableListOf<PluginDownloader>()
    for (downloader in downloaders) {
      try {
        if (downloader.id !in disabledToUpdate && downloader.prepareToInstall(indicator)) {
          readyToInstall += downloader
        }
        indicator.checkCanceled()
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.info(e)
      }
    }
    return readyToInstall
  }

  @JvmStatic
  @RequiresBackgroundThread
  fun installPluginUpdates(downloaders: Collection<PluginDownloader>, indicator: ProgressIndicator): Boolean {
    val downloadedPluginUpdates = downloadPluginUpdates(downloaders, indicator)
    if (downloadedPluginUpdates.isEmpty()) {
      return false
    }

    ProgressManager.getInstance().executeNonCancelableSection {
      for (downloader in downloadedPluginUpdates) {
        try {
          downloader.install()
        }
        catch (e: Exception) {
          LOG.info(e)
        }
      }
    }

    return true
  }

  fun cleanupPatch() {
    NioFiles.deleteRecursively(getTempDir())
  }

  @JvmStatic
  @Throws(IOException::class)
  fun preparePatchCommand(patchFiles: List<Path>, indicator: ProgressIndicator): Array<String> {
    indicator.text = IdeBundle.message("update.preparing.patch.progress")

    val tempDir = getTempDir()
    if (PathManager.isUnderHomeDirectory(tempDir)) {
      throw IOException("Temp directory inside installation: ${tempDir}")
    }
    Files.createDirectories(tempDir)

    var jre = Path.of(System.getProperty("java.home"))
    if (PathManager.isUnderHomeDirectory(jre)) {
      val jreCopy = tempDir.resolve("jre")
      NioFiles.deleteRecursively(jreCopy)
      NioFiles.copyRecursively(jre, jreCopy)
      jre = jreCopy
    }

    val args = mutableListOf<String>()
    val ideHome = PathManager.getHomeDir()

    if (OS.CURRENT == OS.Windows && !Files.isWritable(ideHome)) {
      val launcher = PathManager.findBinFile("launcher.exe")
      val elevator = PathManager.findBinFile("elevator.exe")  // "launcher" depends on "elevator"
      if (launcher != null && elevator != null) {
        args.add(Files.copy(launcher, tempDir.resolve(launcher.fileName), StandardCopyOption.REPLACE_EXISTING).toString())
        Files.copy(elevator, tempDir.resolve(elevator.fileName), StandardCopyOption.REPLACE_EXISTING)
      }
    }

    args += jre.resolve(if (OS.CURRENT == OS.Windows) "bin\\java.exe" else "bin/java").toString()
    args += "-Xmx${2000}m"
    currentJavaVersion().takeIf { it.feature >= 25 }?.let { args += "--enable-native-access=ALL-UNNAMED" }
    args += "-cp"
    args += patchFiles.last().toString()

    args += "-Djna.nosys=true"
    args += "-Djna.boot.library.path="
    args += "-Djna.debug_load=true"
    args += "-Djna.debug_load.jna=true"
    args += "-Djava.io.tmpdir=${tempDir}"
    args += "-Didea.updater.log=${PathManager.getLogDir()}"
    System.getProperty("sun.java2d.metal")?.let { args += "-Dsun.java2d.metal=${it}" }
    System.getProperty("awt.toolkit.name")?.let { args += "-Dawt.toolkit.name=${it}" }
    args += "-Dswing.defaultlaf=${UIManager.getSystemLookAndFeelClassName()}"
    args += "-Duser.language=${DynamicBundle.getLocale().language}"
    args += "-Duser.country=${DynamicBundle.getLocale().country}"

    args += UPDATER_MAIN_CLASS
    args += if (patchFiles.size == 1) "install" else "batch-install"
    args += ideHome.toString()
    if (patchFiles.size > 1) {
      args += patchFiles.joinToString(@Suppress("IO_FILE_USAGE") java.io.File.pathSeparator)
    }

    return args.toTypedArray()
  }

  private fun getTempDir() = PathManager.getTempDir().resolve("patch-update")
}
