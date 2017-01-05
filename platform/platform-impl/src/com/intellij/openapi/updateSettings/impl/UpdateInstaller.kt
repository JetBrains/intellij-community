/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.Restarter
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.IOException
import java.net.URL
import javax.swing.UIManager

object UpdateInstaller {
  private val patchesUrl: String
    get() = System.getProperty("idea.patches.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls.patchesUrl

  @JvmStatic
  @Throws(IOException::class)
  fun installPlatformUpdate(patch: PatchInfo, toBuild: BuildNumber, forceHttps: Boolean, indicator: ProgressIndicator): Array<String> {
    indicator.text = IdeBundle.message("update.downloading.patch.progress")

    val product = ApplicationInfo.getInstance().build.productCode
    val from = patch.fromBuild.asStringWithoutProductCode()
    val to = toBuild.asStringWithoutProductCode()
    val jdk = if (System.getProperty("idea.java.redist", "").lastIndexOf("NoJavaDistribution") >= 0) "-no-jdk" else ""
    val patchName = "${product}-${from}-${to}-patch${jdk}-${patch.osSuffix}.jar"

    val baseUrl = patchesUrl
    val url = URL(URL(if (baseUrl.endsWith('/')) baseUrl else baseUrl + '/'), patchName)
    val patchFile = File(getTempDir(), "patch.jar")
    HttpRequests.request(url.toString()).gzip(false).forceHttps(forceHttps).saveToFile(patchFile, indicator)

    indicator.text = IdeBundle.message("update.preparing.patch.progress")
    return preparePatchCommand(patchFile)
  }

  @JvmStatic
  fun installPluginUpdates(downloaders: Collection<PluginDownloader>, indicator: ProgressIndicator): Boolean {
    indicator.text = IdeBundle.message("progress.downloading.plugins")

    var installed = false

    val disabledToUpdate = UpdateChecker.disabledToUpdatePlugins
    for (downloader in downloaders) {
      try {
        if (downloader.pluginId !in disabledToUpdate && downloader.prepareToInstall(indicator)) {
          val descriptor = downloader.descriptor
          if (descriptor != null) {
            downloader.install()
            installed = true
          }
        }
      }
      catch (e: Exception) {
        Logger.getInstance(UpdateChecker::class.java).info(e)
      }
    }

    return installed
  }

  @JvmStatic
  fun cleanupPatch() {
    val tempDir = getTempDir()
    if (tempDir.exists()) FileUtil.delete(tempDir)
  }

  private fun preparePatchCommand(patchFile: File): Array<String> {
    val log4j = findLib("log4j.jar")
    val jna = findLib("jna.jar")
    val jnaUtils = findLib("jna-platform.jar")

    val tempDir = getTempDir()
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

    if (SystemInfo.isWindows) {
      val launcher = File(PathManager.getBinPath(), "VistaLauncher.exe")
      if (launcher.canExecute()) {
        args += Restarter.createTempExecutable(launcher).path
      }
    }

    args += File(java, if (SystemInfo.isWindows) "bin\\java.exe" else "bin/java").path
    args += "-Xmx750m"
    args += "-cp"
    args += arrayOf(patchFile.path, log4jCopy.path, jnaCopy.path, jnaUtilsCopy.path).joinToString(File.pathSeparator)

    args += "-Djna.nosys=true"
    args += "-Djna.boot.library.path="
    args += "-Djna.debug_load=true"
    args += "-Djna.debug_load.jna=true"
    args += "-Djava.io.tmpdir=${tempDir.path}"
    args += "-Didea.updater.log=${PathManager.getLogPath()}"
    args += "-Dswing.defaultlaf=${UIManager.getSystemLookAndFeelClassName()}"

    args += "com.intellij.updater.Runner"
    args += "install"
    args += PathManager.getHomePath()

    return ArrayUtil.toStringArray(args)
  }

  private fun findLib(libName: String): File {
    val libFile = File(PathManager.getLibPath(), libName)
    return if (libFile.exists()) libFile else throw IOException("Missing: ${libFile}")
  }

  private fun getTempDir() = File(PathManager.getTempPath(), "patch-update")
}