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
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.copyTo

internal object LangDownloader {
  fun download(lang: Lang, project: Project?): Boolean {
    // check if language lib already loaded
    if (GrazieRemote.isAvailableLocally(lang)) return true

    val result = runDownload(lang, project) ?: return false
    check(GrazieRemote.isValidBundleForLanguage(lang, result)) { "Language bundle checksum became invalid right before loading it" }
    val classLoader = UrlClassLoader.build().parent(GraziePlugin.classLoader).files(listOf(result)).get()
    GrazieDynamic.addDynClassLoader(classLoader)
    // force reloading available language classes
    GrazieConfig.update { it.copy() }
    // drop caches, restart highlighting
    GrazieConfig.stateChanged(GrazieConfig.get(), GrazieConfig.get())
    return true
  }

  private fun runDownload(language: Lang, project: Project?): Path? {
    try {
      val presentableName = msg("grazie.settings.proofreading.languages.download.name", language.nativeName)
      return ProgressManager.getInstance().runProcessWithProgressSynchronously<Path, Exception>(
        { performDownload(language, presentableName) },
        presentableName,
        false,
        project
      )
    } catch (exception: Throwable) {
      thisLogger().warn(exception)
      return promptToSelectLanguageBundleManually(project, language)
    }
  }

  @Throws(IllegalStateException::class)
  private fun performDownload(language: Lang, presentableName: @Nls String): Path? {
    val bundle = doDownload(language, presentableName)
    if (!GrazieRemote.isValidBundleForLanguage(language, bundle)) {
      FileUtil.delete(bundle)
      throw IllegalStateException("Failed to verify integrity of downloaded language bundle for language ${language.nativeName}.")
    }
    return bundle
  }

  private fun promptToSelectLanguageBundleManually(project: Project?, language: Lang): Path? {
    val selectedFile = OfflineLanguageBundleSelectionDialog.show(project, language) ?: return null
    val targetPath = GrazieDynamic.dynamicFolder.resolve(language.remote.fileName)
    selectedFile.copyTo(targetPath, overwrite = true)
    return targetPath
  }

  private fun doDownload(lang: Lang, presentableName: @Nls String): Path {
    val downloaderService = DownloadableFileService.getInstance()
    val downloader = downloaderService.createDownloader(
      listOf(downloaderService.createFileDescription(lang.remote.url, lang.remote.fileName)),
      presentableName
    )
    val result = downloader.download(GrazieDynamic.dynamicFolder.toFile()).map { it.first.toPath() }
    return result.single()
  }
}
