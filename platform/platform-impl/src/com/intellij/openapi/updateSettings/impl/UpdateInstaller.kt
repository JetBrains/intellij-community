// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.Restarter
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.UIManager

object UpdateInstaller {
  private val patchesUrl: URL
    get() = URL(System.getProperty("idea.patches.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls.patchesUrl)

  @JvmStatic
  @Throws(IOException::class)
  fun downloadPatchFile(patch: PatchInfo, toBuild: BuildNumber, forceHttps: Boolean, indicator: ProgressIndicator): File {
    indicator.text = IdeBundle.message("update.downloading.patch.progress")
    val product = ApplicationInfo.getInstance().build.productCode
    val from = patch.fromBuild.withoutProductCode().asString()
    val to = toBuild.withoutProductCode().asString()
    val jdk = if (System.getProperty("idea.java.redist", "").lastIndexOf("NoJavaDistribution") >= 0) "-no-jdk" else ""
    val patchName = "${product}-${from}-${to}-patch${jdk}-${PatchInfo.OS_SUFFIX}.jar"
    val url = URL(patchesUrl, patchName).toString()
    val patchFile = File(getTempDir(), "patch.jar")
    HttpRequests.request(url).gzip(false).forceHttps(forceHttps).saveToFile(patchFile, indicator)
    return patchFile
  }

  @JvmStatic
  @Throws(IOException::class)
  fun downloadPatchChain(chain: List<BuildNumber>, forceHttps: Boolean, indicator: ProgressIndicator): List<File> {
    indicator.text = IdeBundle.message("update.downloading.patch.progress")

    val files = mutableListOf<File>()
    val product = ApplicationInfo.getInstance().build.productCode
    val jdk = if (System.getProperty("idea.java.redist", "").lastIndexOf("NoJavaDistribution") >= 0) "-no-jdk" else ""
    val share = 1.0 / (chain.size - 1)

    for (i in 1 until chain.size) {
      val from = chain[i - 1].withoutProductCode().asString()
      val to = chain[i].withoutProductCode().asString()
      val patchName = "${product}-${from}-${to}-patch${jdk}-${PatchInfo.OS_SUFFIX}.jar"
      val patchFile = File(getTempDir(), "patch${i}.jar")
      val url = URL(patchesUrl, patchName).toString()
      val partIndicator = object : DelegatingProgressIndicator(indicator) {
        override fun setFraction(fraction: Double) {
          super.setFraction((i - 1) * share + fraction / share)
        }
      }
      HttpRequests.request(url).gzip(false).forceHttps(forceHttps).saveToFile(patchFile, partIndicator)
      files += patchFile
    }

    return files
  }

  @JvmStatic
  fun installPluginUpdates(downloaders: Collection<PluginDownloader>, indicator: ProgressIndicator): Boolean {
    indicator.text = IdeBundle.message("update.downloading.plugins.progress")

    UpdateChecker.saveDisabledToUpdatePlugins()

    val disabledToUpdate = UpdateChecker.disabledToUpdatePlugins
    val readyToInstall = mutableListOf<PluginDownloader>()
    for (downloader in downloaders) {
      try {
        if (downloader.pluginId !in disabledToUpdate && downloader.prepareToInstall(indicator)) {
          readyToInstall += downloader
        }
        indicator.checkCanceled()
      }
      catch (e: ProcessCanceledException) { throw e }
      catch (e: Exception) {
        Logger.getInstance(UpdateChecker::class.java).info(e)
      }
    }

    var installed = false


    ProgressManager.getInstance().executeNonCancelableSection {
      for (downloader in readyToInstall) {
        try {
          downloader.install()
          installed = true
        }
        catch (e: Exception) {
          Logger.getInstance(UpdateChecker::class.java).info(e)
        }
      }
    }
    return installed
  }

  @JvmStatic
  fun cleanupPatch() {
    val tempDir = getTempDir()
    if (tempDir.exists()) FileUtil.delete(tempDir)
  }

  @JvmStatic
  @Throws(IOException::class)
  fun preparePatchCommand(patchFile: File, indicator: ProgressIndicator): Array<String> =
    preparePatchCommand(listOf(patchFile), indicator)

  @JvmStatic
  @Throws(IOException::class)
  fun preparePatchCommand(patchFiles: List<File>, indicator: ProgressIndicator): Array<String> {
    indicator.text = IdeBundle.message("update.preparing.patch.progress")

    val log4j = findLib("log4j.jar")
    val jna = findLib("jna.jar")
    val jnaUtils = findLib("jna-platform.jar")

    val tempDir = getTempDir()
    if (FileUtil.isAncestor(PathManager.getHomePath(), tempDir.path, true)) {
      throw IOException("Temp directory inside installation: $tempDir")
    }
    if (!(tempDir.exists() || tempDir.mkdirs())) {
      throw IOException("Cannot create temp directory: $tempDir")
    }

    val log4jCopy = log4j.copyTo(File(tempDir, log4j.name), true)
    val jnaCopy = jna.copyTo(File(tempDir, jna.name), true)
    val jnaUtilsCopy = jnaUtils.copyTo(File(tempDir, jnaUtils.name), true)

    var java = System.getProperty("java.home")
    if (FileUtil.isAncestor(PathManager.getHomePath(), java, true)) {
      val javaCopy = File(tempDir, "jre")
      FileUtil.copyDir(File(java), javaCopy)
      java = javaCopy.path
    }

    val args = arrayListOf<String>()

    if (SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath()))) {
      val launcher = PathManager.findBinFile("launcher.exe")
      val elevator = PathManager.findBinFile("elevator.exe")  // "launcher" depends on "elevator"
      if (launcher != null && elevator != null && launcher.canExecute() && elevator.canExecute()) {
        args += Restarter.createTempExecutable(launcher).path
        Restarter.createTempExecutable(elevator)
      }
    }

    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    args += "-Xmx750m"
    args += "-cp"
    args += arrayOf(patchFiles.last().path, log4jCopy.path, jnaCopy.path, jnaUtilsCopy.path).joinToString(File.pathSeparator)

    args += "-Djna.nosys=true"
    args += "-Djna.boot.library.path="
    args += "-Djna.debug_load=true"
    args += "-Djna.debug_load.jna=true"
    args += "-Djava.io.tmpdir=${tempDir.path}"
    args += "-Didea.updater.log=${PathManager.getLogPath()}"
    args += "-Dswing.defaultlaf=${UIManager.getSystemLookAndFeelClassName()}"

    args += "com.intellij.updater.Runner"
    args += if (patchFiles.size == 1) "install" else "batch-install"
    args += PathManager.getHomePath()
    if (patchFiles.size > 1) {
      args += patchFiles.joinToString(File.pathSeparator)
    }

    return ArrayUtil.toStringArray(args)
  }

  private fun findLib(libName: String): File {
    val libFile = File(PathManager.getLibPath(), libName)
    return if (libFile.exists()) libFile else throw IOException("Missing: ${libFile}")
  }

  private fun getTempDir() = File(PathManager.getTempPath(), "patch-update")
}