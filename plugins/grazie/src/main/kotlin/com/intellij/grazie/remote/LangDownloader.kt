// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.isAvailableLocally
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.lang.UrlClassLoader
import java.nio.file.Path
import kotlin.io.path.copyTo

@Suppress("DialogTitleCapitalization")
internal object LangDownloader {
  fun download(lang: Lang, project: Project?): Boolean {
    // check if language lib already loaded
    if (isAvailableLocally(lang)) return true

    val path = runDownload(lang, project) ?: return false
    performGrazieUpdate(listOf(lang to path))
    return true
  }

  fun downloadAsync(languages: Collection<Lang>, project: Project) {
    // check if language lib already loaded
    val notAvailableLocallyLanguages = languages
      .filter { !isAvailableLocally(it) }
    if (notAvailableLocallyLanguages.isEmpty()) return

    val task = object : Task.Backgroundable(
      project,
      msg("grazie.settings.proofreading.languages.download"),
      true,
      ALWAYS_BACKGROUND
    ) {
      override fun run(indicator: ProgressIndicator) {
        performGrazieUpdate(
          performDownload(languages)
        )
      }
    }
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      task, BackgroundableProcessIndicator(task)
    )
  }

  private fun runDownload(language: Lang, project: Project?): Path? {
    try {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Pair<Lang, Path>>, Exception>(
        { performDownload(listOf(language)) },
        msg("grazie.settings.proofreading.languages.download"),
        false,
        project
      ).single().second
    }
    catch (exception: Throwable) {
      thisLogger().warn(exception)
      return promptToSelectLanguageBundleManually(project, language)
    }
  }

  private fun performGrazieUpdate(bundles: List<Pair<Lang, Path>>) {
    bundles.forEach { (lang, path) ->
      check(GrazieRemote.isValidBundleForLanguage(lang, path)) { "Language bundle checksum became invalid right before loading it: $lang" }
    }
    val classLoader = UrlClassLoader.build()
      .parent(GraziePlugin.classLoader)
      .files(bundles.map { it.second })
      .get()
    GrazieDynamic.addDynClassLoader(classLoader)
    // force reloading available language classes
    GrazieConfig.update { it.copy() }
    // drop caches, restart highlighting
    GrazieConfig.stateChanged(GrazieConfig.get(), GrazieConfig.get())
  }

  @Throws(IllegalStateException::class)
  private fun performDownload(languages: Collection<Lang>): List<Pair<Lang, Path>> {
    val bundles = doDownload(languages)
    val invalidBundles = bundles
      .filter { !GrazieRemote.isValidBundleForLanguage(it.first, it.second) }
      .map { it.second }
    if (invalidBundles.isNotEmpty()) {
      bundles.forEach { NioFiles.deleteRecursively(it.second) }
      throw IllegalStateException("Failed to verify integrity of downloaded language bundle for languages ${invalidBundles}.")
    }
    return bundles
  }

  private fun promptToSelectLanguageBundleManually(project: Project?, language: Lang): Path? {
    val selectedFile = OfflineLanguageBundleSelectionDialog.show(project, language) ?: return null
    val targetPath = GrazieDynamic.dynamicFolder.resolve(language.remote.fileName)
    selectedFile.copyTo(targetPath, overwrite = true)
    return targetPath
  }

  private fun doDownload(languages: Collection<Lang>): List<Pair<Lang, Path>> {
    val downloaderService = DownloadableFileService.getInstance()
    val descriptors = languages
      .map { downloaderService.createFileDescription(it.remote.url, it.remote.fileName) }
    val paths = downloaderService
      .createDownloader(descriptors, msg("grazie.settings.proofreading.languages.download"))
      .download(GrazieDynamic.dynamicFolder.toFile())
      .map { it.first.toPath() }
    return languages.zip(paths)
  }
}