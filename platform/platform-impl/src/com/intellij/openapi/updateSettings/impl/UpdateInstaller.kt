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
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.io.HttpRequests
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*

object UpdateInstaller {
  private val patchesUrl: String
    get() = System.getProperty("idea.patches.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls.patchesUrl

  @JvmStatic
  @Throws(IOException::class)
  fun installPlatformUpdate(patch: PatchInfo, toBuild: BuildNumber, forceHttps: Boolean, indicator: ProgressIndicator) {
    indicator.text = IdeBundle.message("update.downloading.patch.progress.title")

    val productCode = ApplicationInfo.getInstance().build.productCode
    val fromBuildNumber = patch.fromBuild.asStringWithoutProductCode()
    val toBuildNumber = toBuild.asStringWithoutProductCode()

    var bundledJdk = ""
    val jdkRedist = System.getProperty("idea.java.redist")
    if (jdkRedist != null && jdkRedist.lastIndexOf("NoJavaDistribution") >= 0) {
      bundledJdk = "-no-jdk"
    }

    val osSuffix = "-" + patch.osSuffix

    val fileName = "$productCode-$fromBuildNumber-$toBuildNumber-patch$bundledJdk$osSuffix.jar"

    var baseUrl = patchesUrl
    if (!baseUrl.endsWith('/')) baseUrl += '/'

    val url = URL(URL(baseUrl), fileName).toString()
    val tempFile = HttpRequests.request(url)
        .gzip(false)
        .forceHttps(forceHttps)
        .connect { request -> request.saveToFile(FileUtil.createTempFile("ij.platform.", ".patch", true), indicator) }

    val patchFileName = ("jetbrains.patch.jar." + PlatformUtils.getPlatformPrefix()).toLowerCase(Locale.ENGLISH)
    val patchFile = File(FileUtil.getTempDirectory(), patchFileName)
    FileUtil.copy(tempFile, patchFile)
    FileUtil.delete(tempFile)
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
}