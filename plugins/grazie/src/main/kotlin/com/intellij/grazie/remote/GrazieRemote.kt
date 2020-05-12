// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists

object GrazieRemote {
  private fun isLibExists(lib: String) = GrazieDynamic.dynamicFolder.resolve(lib).exists() || GraziePlugin.libFolder.resolve(lib).exists()

  fun isAvailableLocally(lang: Lang) = lang.isEnglish() || isLibExists(lang.remote.fileName)

  fun allAvailableLocally() = Lang.values().filter { isAvailableLocally(it) }

  /** Downloads [lang] to local storage */
  fun download(lang: Lang, project: Project? = null): Boolean {
    if (isAvailableLocally(lang)) return true

    return LangDownloader.download(lang, project)
  }

  /** Downloads all missing languages to local storage*/
  fun downloadMissing(project: Project?) = GrazieConfig.get().missedLanguages.forEach { LangDownloader.download(it, project) }
}
