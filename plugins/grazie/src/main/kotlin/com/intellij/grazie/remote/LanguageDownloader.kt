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
import kotlin.io.path.copyTo

@Suppress("DialogTitleCapitalization")
internal object LanguageDownloader {

  @Deprecated("Use downloadAsync(Collection<Lang>, Project) instead", replaceWith = ReplaceWith("downloadAsync(listOf(lang), project)"))
  @ApiStatus.ScheduledForRemoval
  internal fun download(languages: Collection<Lang>): Boolean {
    if (allAvailableLocally(languages)) return true
    runDownload(languages)
    performGrazieUpdate(languages)
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
    withContext(Dispatchers.IO) {
      try {
        performDownload(languages)
      }
      catch (exception: Throwable) {
        val installedSuccessfully = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          promptToSelectLanguageBundleManually(languages)
        }
        thisLogger().warn(exception)
        if (!installedSuccessfully) throw exception
      }
    }
    performGrazieUpdate(languages)
  }

  private fun runDownload(languages: Collection<Lang>) {
    try {
      runWithModalProgressBlocking(
        ModalTaskOwner.guess(),
        msg("grazie.settings.proofreading.languages.download")
      ) {
        performDownload(languages)
      }
    }
    catch (exception: Throwable) {
      thisLogger().warn(exception)
      if (!promptToSelectLanguageBundleManually(languages)) throw exception
    }
  }

  private fun performGrazieUpdate(languages: Collection<Lang>) {
    languages.forEach {
      val jarPath = GrazieDynamic.dynamicFolder.resolve(it.ltRemote!!.storageName)
      check(GrazieRemote.isValidBundleForLanguage(it.ltRemote, jarPath)) {
        "Language bundle checksum became invalid right before loading it: $it"
      }
    }
    val classLoader = UrlClassLoader.build()
      .parent(GraziePlugin.classLoader)
      .files(languages.map { GrazieDynamic.dynamicFolder.resolve(it.ltRemote!!.storageName) })
      .get()
    GrazieDynamic.addDynClassLoader(classLoader)
    languages
      .filter { it.hunspellRemote != null }
      .forEach {
        val descriptor = it.hunspellRemote!!
        val jarPath = GrazieDynamic.dynamicFolder.resolve(descriptor.storageDescriptor)
        check(GrazieRemote.isValidBundleForLanguage(it.hunspellRemote, jarPath)) {
          "Language bundle checksum became invalid right before loading it: $it"
        }
        val outputDir = GrazieDynamic.dynamicFolder.resolve(descriptor.storageName)
        Files.createDirectories(outputDir)
        ZipUtil.extract(jarPath, outputDir, HunspellDescriptor.filenameFilter())
        NioFiles.deleteRecursively(jarPath)
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
  private fun performDownload(languages: Collection<Lang>) {
    val bundles = downloadLanguages(languages)
    val invalidBundles = languages
      .flatMap { it.remoteDescriptors }
      .map { it to GrazieDynamic.dynamicFolder.resolve(it.storageDescriptor) }
      .filterNot { GrazieRemote.isValidBundleForLanguage(it.first, it.second) }
      .map { it.second }
    if (invalidBundles.isNotEmpty()) {
      deleteLanguages()
      throw IllegalStateException("Failed to verify integrity of downloaded language bundle for languages ${invalidBundles}.")
    }
    return bundles
  }


  /**
   * @return true if manual installation was successful, false otherwise
   */
  private fun promptToSelectLanguageBundleManually(languages: Collection<Lang>): Boolean {
    val urls = languages.flatMap { it.remoteDescriptors }.joinToString("\n") { it.url }
    thisLogger().info(
      "Please download the following JARs and select them in the 'Choose Languages' Bundles' dialog for manual offline installation.\n$urls"
    )
    val selectedFiles = OfflineLanguageBundleSelectionDialog.show(languages)
    selectedFiles.forEach { file ->
      file.copyTo(GrazieDynamic.dynamicFolder.resolve(file.fileName), overwrite = true)
    }
    return selectedFiles.isNotEmpty()
  }

  private fun downloadLanguages(languages: Collection<Lang>) {
    val downloaderService = DownloadableFileService.getInstance()
    try {
      val descriptors = languages.flatMap { it.remoteDescriptors }
        .map { it.url to it.storageDescriptor }
        .map { downloaderService.createFileDescription(it.first, it.second) }
      downloaderService
        .createDownloader(descriptors, msg("grazie.settings.proofreading.languages.download"))
        .download(GrazieDynamic.dynamicFolder.toFile())
    }
    catch (e: Exception) {
      deleteLanguages()
      throw e
    }
  }

  private fun deleteLanguages() {
    Files.list(GrazieDynamic.dynamicFolder).forEach {
      NioFiles.deleteRecursively(it)
    }
  }
}