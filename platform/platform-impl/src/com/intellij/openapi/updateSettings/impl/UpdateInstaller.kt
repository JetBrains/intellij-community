// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.copy
import com.intellij.util.system.CpuArch
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.swing.UIManager

internal object UpdateInstaller {
  const val UPDATER_MAIN_CLASS = "com.intellij.updater.Runner"

  private val LOG = logger<UpdateInstaller>()

  private const val PATCH_FILE_NAME = "patch-file.zip"
  private const val UPDATER_ENTRY = "com/intellij/updater/Runner.class"

  private val patchesUrl: URL
    get() = URL(System.getProperty("idea.patches.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls!!.patchesUrl)

  @JvmStatic
  @Throws(IOException::class)
  fun downloadPatchChain(chain: List<BuildNumber>, indicator: ProgressIndicator): List<File> {
    indicator.text = IdeBundle.message("update.downloading.patch.progress")

    val files = mutableListOf<File>()
    val product = ApplicationInfo.getInstance().build.productCode
    val jdk = getJdkSuffix()
    val share = 1.0 / (chain.size - 1)

    for (i in 1 until chain.size) {
      val from = chain[i - 1].withoutProductCode().asString()
      val to = chain[i].withoutProductCode().asString()
      val patchName = "${product}-${from}-${to}-patch${jdk}-${PatchInfo.OS_SUFFIX}.jar"
      val patchFile = File(getTempDir(), patchName)
      val url = URL(patchesUrl, patchName).toString()
      val partIndicator = object : DelegatingProgressIndicator(indicator) {
        override fun setFraction(fraction: Double) {
          super.setFraction((i - 1) * share + fraction / share)
        }
      }
      LOG.info("downloading ${url}")
      HttpRequests.request(url).gzip(false).saveToFile(patchFile, partIndicator)
      try {
        ZipFile(patchFile).use {
          if (it.getEntry(PATCH_FILE_NAME) == null || it.getEntry(UPDATER_ENTRY) == null) {
            throw IOException("Corrupted patch file: ${patchFile.name}")
          }
        }
      }
      catch (e: ZipException) {
        throw IOException("Corrupted patch file: ${patchFile.name}", e)
      }
      files += patchFile
    }

    return files
  }

  @JvmStatic
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

  @JvmStatic
  fun cleanupPatch() {
    val tempDir = getTempDir()
    if (tempDir.exists()) FileUtil.delete(tempDir)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun preparePatchCommand(patchFiles: List<File>, indicator: ProgressIndicator): Array<String> {
    indicator.text = IdeBundle.message("update.preparing.patch.progress")

    val tempDir = getTempDir()
    if (FileUtil.isAncestor(PathManager.getHomePath(), tempDir.path, true)) {
      throw IOException("Temp directory inside installation: $tempDir")
    }
    if (!(tempDir.exists() || tempDir.mkdirs())) {
      throw IOException("Cannot create temp directory: $tempDir")
    }

    var java = System.getProperty("java.home")
    if (PathManager.isUnderHomeDirectory(Path.of(java))) {
      val javaCopy = File(tempDir, "jre")
      if (javaCopy.exists()) FileUtil.delete(javaCopy)
      FileUtil.copyDir(File(java), javaCopy)

      val jnf = File(java, "../Frameworks/JavaNativeFoundation.framework")
      if (jnf.isDirectory) {
        val jnfCopy = File(tempDir, "Frameworks/JavaNativeFoundation.framework")
        if (jnfCopy.exists()) FileUtil.delete(jnfCopy)
        FileUtil.copyDir(jnf, jnfCopy)
      }

      java = javaCopy.path
    }

    val args = mutableListOf<String>()

    if (SystemInfo.isWindows && !Files.isWritable(Path.of(PathManager.getHomePath()))) {
      val launcher = PathManager.findBinFile("launcher.exe")
      val elevator = PathManager.findBinFile("elevator.exe")  // "launcher" depends on "elevator"
      if (launcher != null && elevator != null && Files.isExecutable(launcher) && Files.isExecutable(elevator)) {
        args.add(launcher.copy(tempDir.toPath().resolve(launcher.fileName)).toString())
        elevator.copy(tempDir.toPath().resolve(elevator.fileName))
      }
    }

    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    args += "-Xmx${2000}m"
    args += "-cp"
    args += patchFiles.last().path

    args += "-Djna.nosys=true"
    args += "-Djna.boot.library.path="
    args += "-Djna.debug_load=true"
    args += "-Djna.debug_load.jna=true"
    args += "-Djava.io.tmpdir=${tempDir.path}"
    args += "-Didea.updater.log=${PathManager.getLogPath()}"
    args += "-Dswing.defaultlaf=${UIManager.getSystemLookAndFeelClassName()}"

    args += UPDATER_MAIN_CLASS
    args += if (patchFiles.size == 1) "install" else "batch-install"
    args += PathManager.getHomePath()
    if (patchFiles.size > 1) {
      args += patchFiles.joinToString(File.pathSeparator)
    }

    return args.toTypedArray()
  }

  private fun getTempDir() = File(PathManager.getTempPath(), "patch-update")

  private fun getJdkSuffix(): String = when {
    SystemInfo.isMac && CpuArch.isArm64() -> "-jbr11-aarch64"
    Files.isDirectory(Path.of(PathManager.getHomePath(), "jbr")) -> "-jbr11"
    else -> "-no-jbr"
  }
}
