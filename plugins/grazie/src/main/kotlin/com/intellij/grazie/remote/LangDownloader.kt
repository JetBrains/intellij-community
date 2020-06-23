// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.project.Project
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.lang.UrlClassLoader
import java.nio.file.Paths

internal object LangDownloader {
  private val downloader by lazy { DownloadableFileService.getInstance() }

  fun download(lang: Lang, project: Project?): Boolean {
    // check if language lib already loaded
    if (GrazieRemote.isAvailableLocally(lang)) return true

    val result = downloader.createDownloader(
      listOf(downloader.createFileDescription(lang.remote.url, lang.remote.fileName)),
      msg("grazie.settings.proofreading.languages.download.name", lang.nativeName)
    ).downloadFilesWithProgress(GrazieDynamic.dynamicFolder.toRealPath().toString(), project, null)

    // null if canceled or failed, zero result if nothing found
    if (result != null && result.isNotEmpty()) {
      val classLoader = UrlClassLoader.build().parent(GraziePlugin.classLoader)
        .urls(result.map { Paths.get(it.presentableUrl).toUri().toURL() }).get()

      GrazieDynamic.addDynClassLoader(classLoader)

      GrazieConfig.update { state -> state.copy() }
      return true
    }

    return false
  }
}
