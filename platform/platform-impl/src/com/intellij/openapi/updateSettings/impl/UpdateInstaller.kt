// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.copy
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.swing.UIManager

internal object UpdateInstaller {
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
      val partIndicator = object : DelegatingProgressIndicator(indicator) {
        override fun setFraction(fraction: Double) {
          super.setFraction((i - 1) * share + fraction / share)
        }
      }
      LOG.info("downloading ${url}")
      HttpRequests.request(url).gzip(false).saveToFile(patchFile, partIndicator)
      try {
        ZipFile(patchFile.toFile()).use {
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

    UpdateChecker.saveDisabledToUpdatePlugins()

    val disabledToUpdate = UpdateChecker.disabledToUpdate
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

    var java = Path.of(System.getProperty("java.home"))
    if (PathManager.isUnderHomeDirectory(java)) {
      val javaCopy = tempDir.resolve("jre")
      NioFiles.deleteRecursively(javaCopy)
      FileUtil.copyDir(java.toFile(), javaCopy.toFile())

      val jnf = java.resolve("../Frameworks/JavaNativeFoundation.framework")
      if (Files.isDirectory(jnf)) {
        val jnfCopy = tempDir.resolve("Frameworks/JavaNativeFoundation.framework")
        NioFiles.deleteRecursively(jnfCopy)
        FileUtil.copyDir(jnf.toFile(), jnfCopy.toFile())
      }

      java = javaCopy
    }

    val args = mutableListOf<String>()
    val ideHome = PathManager.getHomePath()

    if (SystemInfo.isWindows && !Files.isWritable(Path.of(ideHome))) {
      val launcher = PathManager.findBinFile("launcher.exe")
      val elevator = PathManager.findBinFile("elevator.exe")  // "launcher" depends on "elevator"
      if (launcher != null && elevator != null) {
        args.add(launcher.copy(tempDir.resolve(launcher.fileName)).toString())
        elevator.copy(tempDir.resolve(elevator.fileName))
      }
    }

    args += java.resolve(if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").toString()
    args += "-Xmx${2000}m"
    args += "-cp"
    args += patchFiles.last().toString()

    args += "-Djna.nosys=true"
    args += "-Djna.boot.library.path="
    args += "-Djna.debug_load=true"
    args += "-Djna.debug_load.jna=true"
    args += "-Djava.io.tmpdir=${tempDir}"
    args += "-Didea.updater.log=${PathManager.getLogPath()}"
    args += "-Dswing.defaultlaf=${UIManager.getSystemLookAndFeelClassName()}"
    args += "-Duser.language=${DynamicBundle.getLocale().language}"
    args += "-Duser.country=${DynamicBundle.getLocale().country}"

    args += UPDATER_MAIN_CLASS
    args += if (patchFiles.size == 1) "install" else "batch-install"
    args += ideHome
    if (patchFiles.size > 1) {
      args += patchFiles.joinToString(File.pathSeparator)
    }

    return args.toTypedArray()
  }

  private fun getTempDir() = Path.of(PathManager.getTempPath(), "patch-update")
}
