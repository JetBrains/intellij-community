// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.CommonBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.DigestUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.security.DigestInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream

object GrazieRemote {

  fun isJLangAvailableLocally(lang: Lang): Boolean {
    if (lang.isEnglish()) return true
    return GrazieDynamic.getLangDynamicFolder(lang).resolve(lang.ltRemote!!.file).exists()
  }

  fun isAvailableLocally(lang: Lang): Boolean {
    if (lang.isEnglish()) return true
    return GrazieDynamic.getLangDynamicFolder(lang).exists()
  }

  fun allAvailableLocally(languages: Collection<Lang>): Boolean = languages.all { isAvailableLocally(it) }

  /** Downloads [lang] to local storage */
  @Deprecated("Use downloadAsync(Collection<Lang>, Project) instead", replaceWith = ReplaceWith("downloadAsync(listOf(lang), project)"))
  @ApiStatus.ScheduledForRemoval
  fun download(lang: Lang): Boolean = LanguageDownloader.download(lang)

  /** Downloads [languages] asynchronously to local storage */
  fun downloadAsync(languages: Collection<Lang>, project: Project): Unit = LanguageDownloader.downloadAsync(languages, project)

  /** Downloads all missing languages to local storage*/
  fun downloadMissing(project: Project): Unit = downloadAsync(GrazieConfig.get().missedLanguages, project)

  /**
   * Get user agreement before downloading licensed language bundle
   * @return true if the user agrees with license, false if the user doesn't agree or agreement isn't required
   */
  @RequiresEdt
  fun getLanguagesBasedOnUserAgreement(languages: Collection<Lang>, project: Project): Collection<Lang> {
    val gplLanguages = languages
      .filter { it.hunspellRemote?.isGplLicensed == true }
      .toList()
    if (gplLanguages.isEmpty()) return languages

    val hasUserAgreement = Messages.showOkCancelDialog(
      project,
      msg("grazie.license.gpl.message", gplLanguages.joinToString { it.shortDisplayName }),
      msg("grazie.license.gpl.title"),
      CommonBundle.getOkButtonText(),
      if (languages.size == gplLanguages.size) CommonBundle.getCancelButtonText() else msg("grazie.license.gpl.cancel"),
      Messages.getQuestionIcon()
    ) == Messages.OK
    if (hasUserAgreement) return languages
    return languages.filter { it.hunspellRemote?.isGplLicensed != true }
  }

  fun isValidBundleForLanguage(language: Lang, file: Path): Boolean {
    val remote = language.ltRemote ?: return false
    val actualChecksum = checksum(file)
    return remote.checksum == actualChecksum
  }

  @ApiStatus.Internal
  fun checksum(path: Path): String {
    val digest = path.inputStream().use {
      val digest = DigestUtil.md5()
      DigestInputStream(it, digest).use { stream ->
        val buffer = ByteArray(1024 * 8)
        var bytesRead = 0
        while (bytesRead != -1) {
          bytesRead = stream.read(buffer)
        }
      }
      return@use digest
    }
    return DigestUtil.digestToHash(digest)
  }
}
