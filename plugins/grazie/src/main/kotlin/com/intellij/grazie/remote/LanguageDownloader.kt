// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.GrazieScope
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.GrazieRemote.allAvailableLocally
import com.intellij.grazie.remote.GrazieRemote.getLanguagesBasedOnUserAgreement
import com.intellij.grazie.remote.GrazieRemote.isAvailableLocally
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.io.ZipUtil
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo

@Suppress("DialogTitleCapitalization")
internal object LanguageDownloader {

  @Deprecated("Use downloadAsync(Collection<Lang>, Project) instead", replaceWith = ReplaceWith("downloadAsync(listOf(lang), project)"))
  @ApiStatus.ScheduledForRemoval
  fun download(lang: Lang): Boolean {
    if (isAvailableLocally(lang)) return true
    val path = runDownload(lang) ?: return false
    performGrazieUpdate(LanguageBundles(lang to path))
    return true
  }

  fun downloadAsync(languages: Collection<Lang>, project: Project) {
    if (allAvailableLocally(languages)) return
    GrazieScope.coroutineScope().launch {
      val filteredLanguages = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        getLanguagesBasedOnUserAgreement(languages, project)
      }
      if (languages.isEmpty()) return@launch
      withBackgroundProgress(project, msg("grazie.settings.proofreading.languages.download"), true) {
        startDownloading(filteredLanguages)
      }
    }
  }

  suspend fun startDownloading(languages: Collection<Lang>) {
    val bundles = withContext(Dispatchers.IO) {
      performDownload(languages)
    }
    performGrazieUpdate(bundles)
  }

  private fun runDownload(language: Lang): Path? {
    try {
      return runWithModalProgressBlocking(
        ModalTaskOwner.guess(),
        msg("grazie.settings.proofreading.languages.download")
      ) {
        performDownload(listOf(language))
      }.languages.entries.single().value
    }
    catch (exception: Throwable) {
      thisLogger().warn(exception)
      return promptToSelectLanguageBundleManually(language)
    }
  }

  private fun performGrazieUpdate(bundles: LanguageBundles) {
    if (bundles.languages.isNotEmpty()) {
      bundles.languages.forEach { (lang, path) ->
        // Each language has its own LT jar file, at least for now
        val jarPath = path.resolve(lang.ltRemote!!.storageName)
        check(GrazieRemote.isValidBundleForLanguage(lang, jarPath)) { "Language bundle checksum became invalid right before loading it: $lang" }
      }
      val classLoader = UrlClassLoader.build()
        .parent(GraziePlugin.classLoader)
        .files(bundles.languages.map { it.value.resolve(it.key.ltRemote!!.storageName) })
        .get()
      GrazieDynamic.addDynClassLoader(classLoader)
    }
    bundles.hunspellLangs.forEach { (lang, path) ->
      val zip = path.resolve(lang.hunspellRemote!!.storageDescriptor)
      val outputDir = path.resolve(lang.hunspellRemote!!.storageName)
      Files.createDirectories(outputDir)
      ZipUtil.extract(zip, outputDir, HunspellDescriptor.filenameFilter())
      NioFiles.deleteRecursively(zip)
    }
    reloadGrazie()
  }

  private fun reloadGrazie() {
    // force reloading available language classes
    GrazieConfig.update { it.copy() }
    // drop caches, restart highlighting
    GrazieConfig.stateChanged(GrazieConfig.get(), GrazieConfig.get())
  }

  @Throws(IllegalStateException::class)
  private fun performDownload(languages: Collection<Lang>): LanguageBundles {
    val bundles = downloadLanguages(languages)
    val invalidBundles = bundles.jLangs
      .map { (lang, path) -> lang to path.resolve(lang.ltRemote!!.storageName) }
      .filter { !GrazieRemote.isValidBundleForLanguage(it.first, it.second) }
      .map { it.second }
    if (invalidBundles.isNotEmpty()) {
      bundles.languages.forEach { NioFiles.deleteRecursively(it.value) }
      throw IllegalStateException("Failed to verify integrity of downloaded language bundle for languages ${invalidBundles}.")
    }
    return bundles
  }

  private fun promptToSelectLanguageBundleManually(language: Lang): Path? {
    language.ltRemote ?: return null
    val selectedFile = OfflineLanguageBundleSelectionDialog.show(null, language) ?: return null
    val targetPath = GrazieDynamic.getLangDynamicFolder(language).resolve(language.ltRemote!!.storageName)
    selectedFile.copyTo(targetPath, overwrite = true)
    return targetPath
  }

  private fun downloadLanguages(languages: Collection<Lang>): LanguageBundles {
    val downloaderService = DownloadableFileService.getInstance()
    val paths = mutableMapOf<Lang, Path>()
    try {
      languages.forEach { lang ->
        val folder = GrazieDynamic.getLangDynamicFolder(lang)
        val descriptors = lang.remoteDescriptors
          .map { it.url to it.storageDescriptor }
          .map { downloaderService.createFileDescription(it.first, it.second) }
        downloaderService
          .createDownloader(descriptors, msg("grazie.settings.proofreading.languages.download"))
          .download(folder.toFile())
        paths.put(lang, folder)
      }
    }
    catch (e: Exception) {
      paths.forEach { NioFiles.deleteRecursively(it.value) }
      throw e
    }
    return LanguageBundles(paths)
  }

  /**
   * [Path] in this map corresponds to the absolute path of [RemoteLangDescriptor.storageDescriptor]
   */
  private data class LanguageBundles(val languages: Map<Lang, Path>) {
    val hunspellLangs: Map<Lang, Path>
      get() = languages.filterKeys { it.hunspellRemote != null }
    val jLangs: Map<Lang, Path>
      get() = languages.filterKeys { it.ltRemote != null }

    constructor(lang: Pair<Lang, Path>) : this(mapOf(lang))
  }
}