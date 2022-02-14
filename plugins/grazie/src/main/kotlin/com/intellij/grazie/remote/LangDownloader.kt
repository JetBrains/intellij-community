// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.Nls
import java.nio.file.Path

internal object LangDownloader {
  fun download(lang: Lang, project: Project?): Boolean {
    // check if language lib already loaded
    if (GrazieRemote.isAvailableLocally(lang)) return true

    val result = runDownload(lang, project)

    // null if canceled or failed, zero result if nothing found
    if (result != null && result.isNotEmpty()) {
      val classLoader = UrlClassLoader.build().parent(GraziePlugin.classLoader)
        .files(result).get()

      GrazieDynamic.addDynClassLoader(classLoader)

      // drop caches, restart highlighting
      GrazieConfig.stateChanged(GrazieConfig.get(), GrazieConfig.get())
      return true
    }

    return false
  }

  private fun runDownload(lang: Lang, project: Project?): List<Path>? {
    try {
      val presentableName = msg("grazie.settings.proofreading.languages.download.name", lang.nativeName)
      return ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Path>, Exception>(
        { doDownload(lang, presentableName) },
        presentableName,
        false,
        project
      )
    } catch (exception: Throwable) {
      thisLogger().error(exception)
      return null
    }
  }

  private fun doDownload(lang: Lang, presentableName: @Nls String): List<Path> {
    val downloaderService = DownloadableFileService.getInstance()
    val downloader = downloaderService.createDownloader(
      listOf(downloaderService.createFileDescription(lang.remote.url, lang.remote.fileName)),
      presentableName
    )
    return downloader.download(GrazieDynamic.dynamicFolder.toFile()).map { it.first.toPath() }
  }
}
