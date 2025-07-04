// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.project.Project
import com.intellij.util.io.DigestUtil
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.security.DigestInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream

object GrazieRemote {
  private fun isLibExists(lib: String) = GrazieDynamic.dynamicFolder.resolve(lib).exists() || GraziePlugin.libFolder.resolve(lib).exists()

  fun isAvailableLocally(lang: Lang): Boolean = lang.isEnglish() || isLibExists(lang.remote.fileName)

  fun allAvailableLocally(languages: Collection<Lang>): Boolean = languages.all { isAvailableLocally(it) }

  fun allAvailableLocally(): List<Lang> = Lang.entries.filter { isAvailableLocally(it) }

  /** Downloads [lang] to local storage */
  fun download(lang: Lang): Boolean = LanguageDownloader.download(lang)

  /** Downloads [languages] asynchronously to local storage */
  fun downloadAsync(languages: Collection<Lang>, project: Project): Unit = LanguageDownloader.downloadAsync(languages, project)

  /** Downloads all missing languages to local storage*/
  fun downloadMissing(project: Project): Unit = downloadAsync(GrazieConfig.get().missedLanguages, project)

  fun isValidBundleForLanguage(language: Lang, file: Path): Boolean {
    val actualChecksum = checksum(file)
    return language.remote.checksum == actualChecksum
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
