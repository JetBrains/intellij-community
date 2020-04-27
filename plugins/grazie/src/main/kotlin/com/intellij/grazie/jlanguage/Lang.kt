// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.detector.model.LanguageISO
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.RemoteLangDescriptor
import org.languagetool.language.Language

enum class Lang(val displayName: String, val className: String, val remote: RemoteLangDescriptor, val nativeName: String) {
  BRITISH_ENGLISH("English (GB)", "BritishEnglish", RemoteLangDescriptor.ENGLISH, "English (Great Britain)"),
  AMERICAN_ENGLISH("English (US)", "AmericanEnglish", RemoteLangDescriptor.ENGLISH, "English (USA)"),
  CANADIAN_ENGLISH("English (Canadian)", "CanadianEnglish", RemoteLangDescriptor.ENGLISH, "English (Canada)"),
  GERMANY_GERMAN("German (Germany)", "GermanyGerman", RemoteLangDescriptor.GERMAN, "Deutsch (Deutschland)"),
  AUSTRIAN_GERMAN("German (Austria)", "AustrianGerman", RemoteLangDescriptor.GERMAN, "Deutsch (Österreich)"),
  PORTUGAL_PORTUGUESE("Portuguese (Portugal)", "PortugalPortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Portugal)"),
  BRAZILIAN_PORTUGUESE("Portuguese (Brazil)", "BrazilianPortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Brasil)"),
  SPANISH("Spanish", "Spanish", RemoteLangDescriptor.SPANISH, "Español"),
  RUSSIAN("Russian", "Russian", RemoteLangDescriptor.RUSSIAN, "Русский"),
  FRENCH("French", "French", RemoteLangDescriptor.FRENCH, "Français"),
  ITALIAN("Italian", "Italian", RemoteLangDescriptor.ITALIAN, "Italiano"),
  DUTCH("Dutch", "Dutch", RemoteLangDescriptor.DUTCH, "Nederlands"),
  JAPANESE("Japanese", "Japanese", RemoteLangDescriptor.JAPANESE, "日本語"),
  CHINESE("Chinese", "Chinese", RemoteLangDescriptor.CHINESE, "中文"),
  PERSIAN("Persian", "Persian", RemoteLangDescriptor.PERSIAN, "فارسی"),
  POLISH("Polish", "Polish", RemoteLangDescriptor.POLISH, "Polski"),
  GREEK("Greek", "Greek", RemoteLangDescriptor.GREEK, "Ελληνικά"),
  ROMANIAN("Romanian", "Romanian", RemoteLangDescriptor.ROMANIAN, "Română"),
  SLOVAK("Slovak", "Slovak", RemoteLangDescriptor.SLOVAK, "Slovenčina"),
  UKRAINIAN("Ukrainian", "Ukrainian", RemoteLangDescriptor.UKRAINIAN, "Українська");

  companion object {
    fun sortedValues() = values().sortedBy(Lang::nativeName)
  }

  val iso: LanguageISO
    get() = remote.iso

  private var _jLanguage: Language? = null
  val jLanguage: Language?
    get() = _jLanguage ?: GrazieDynamic.loadLang(this)?.also {
      _jLanguage = it
    }

  fun isAvailable() = GrazieRemote.isAvailableLocally(this)

  fun isEnglish() = iso == LanguageISO.EN

  fun equalsTo(language: com.intellij.grazie.detector.model.Language) = iso == language.iso

  override fun toString() = displayName
}

