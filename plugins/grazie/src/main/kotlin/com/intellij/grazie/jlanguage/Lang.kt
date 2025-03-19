// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.RemoteLangDescriptor
import com.intellij.openapi.util.NlsSafe
import org.languagetool.Language
import org.languagetool.language.English
import org.languagetool.noop.NoopChunker

enum class Lang(val displayName: String, val className: String, val remote: RemoteLangDescriptor, @NlsSafe val nativeName: String) {
  BRITISH_ENGLISH("English (GB)", "BritishEnglish", RemoteLangDescriptor.ENGLISH, "English (Great Britain)"),
  AMERICAN_ENGLISH("English (US)", "AmericanEnglish", RemoteLangDescriptor.ENGLISH, "English (USA)"),
  CANADIAN_ENGLISH("English (Canadian)", "CanadianEnglish", RemoteLangDescriptor.ENGLISH, "English (Canada)"),
  ARABIC("Arabic", "Arabic", RemoteLangDescriptor.ARABIC, "العربيةُ"),
  ASTURIAN("Asturian", "Asturian", RemoteLangDescriptor.ASTURIAN, "Asturianu"),
  BELARUSIAN("Belarusian", "Belarusian", RemoteLangDescriptor.BELARUSIAN, "Беларуская"),
  BRETON("Breton", "Breton", RemoteLangDescriptor.BRETON, "Brezhoneg"),
  CATALAN("Catalan", "Catalan", RemoteLangDescriptor.CATALAN, "Català"),
  VALENCIAN_CATALAN("Catalan (Valencian)", "ValencianCatalan", RemoteLangDescriptor.CATALAN, "Català (Valencià)"),
  DANISH("Danish", "Danish", RemoteLangDescriptor.DANISH, "Dansk"),
  GERMANY_GERMAN("German (Germany)", "GermanyGerman", RemoteLangDescriptor.GERMAN, "Deutsch (Deutschland)"),
  AUSTRIAN_GERMAN("German (Austria)", "AustrianGerman", RemoteLangDescriptor.GERMAN, "Deutsch (Österreich)"),
  SWISS_GERMAN("German (Switzerland)", "SwissGerman", RemoteLangDescriptor.GERMAN, "Deutsch (Die Schweiz)"),
  GREEK("Greek", "Greek", RemoteLangDescriptor.GREEK, "Ελληνικά"),
  ESPERANTO("Esperanto", "Esperanto", RemoteLangDescriptor.ESPERANTO, "Esperanto"),
  SPANISH("Spanish", "Spanish", RemoteLangDescriptor.SPANISH, "Español"),
  PERSIAN("Persian", "Persian", RemoteLangDescriptor.PERSIAN, "فارسی"),
  FRENCH("French", "French", RemoteLangDescriptor.FRENCH, "Français"),
  IRISH("Irish", "Irish", RemoteLangDescriptor.IRISH, "Gaeilge"),
  GALICIAN("Galician", "Galician", RemoteLangDescriptor.GALICIAN, "Galego"),
  ITALIAN("Italian", "Italian", RemoteLangDescriptor.ITALIAN, "Italiano"),
  JAPANESE("Japanese", "Japanese", RemoteLangDescriptor.JAPANESE, "日本語"),
  KHMER("Khmer", "Khmer", RemoteLangDescriptor.KHMER, "ភាសាខ្មែរ"),
  DUTCH("Dutch", "Dutch", RemoteLangDescriptor.DUTCH, "Nederlands"),
  POLISH("Polish", "Polish", RemoteLangDescriptor.POLISH, "Polski"),
  PORTUGAL_PORTUGUESE("Portuguese (Portugal)", "PortugalPortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Portugal)"),
  BRAZILIAN_PORTUGUESE("Portuguese (Brazil)", "BrazilianPortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Brasil)"),
  ANGOLA_PORTUGUESE("Portuguese (Angola)", "AngolaPortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Angola)"),
  MOZAMBIQUE_PORTUGUESE("Portuguese (Mozambique)", "MozambiquePortuguese", RemoteLangDescriptor.PORTUGUESE, "Português (Moçambique)"),
  ROMANIAN("Romanian", "Romanian", RemoteLangDescriptor.ROMANIAN, "Română"),
  RUSSIAN("Russian", "Russian", RemoteLangDescriptor.RUSSIAN, "Русский"),
  SLOVAK("Slovak", "Slovak", RemoteLangDescriptor.SLOVAK, "Slovenčina"),
  SLOVENIAN("Slovenian", "Slovenian", RemoteLangDescriptor.SLOVENIAN, "Slovenščina"),
  SWEDISH("Swedish", "Swedish", RemoteLangDescriptor.SWEDISH, "Svenska"),
  TAMIL("Tamil", "Tamil", RemoteLangDescriptor.TAMIL, "தமிழ்"),
  TAGALOG("Tagalog", "Tagalog", RemoteLangDescriptor.TAGALOG, "Tagalog"),
  UKRAINIAN("Ukrainian", "Ukrainian", RemoteLangDescriptor.UKRAINIAN, "Українська"),
  CHINESE("Chinese", "Chinese", RemoteLangDescriptor.CHINESE, "中文");

  companion object {
    fun sortedValues() = values().sortedBy(Lang::nativeName)

    // the chunker can be very memory-, disk- and CPU-expensive
    internal fun shouldDisableChunker(language: Language): Boolean = language is English
  }

  val iso: LanguageISO
    get() = remote.iso

  private var _jLanguage: Language? = null
  val jLanguage: Language?
    get() = _jLanguage ?: GrazieDynamic.loadLang(this)?.also {
      if (shouldDisableChunker(it)) {
        it.chunker = NoopChunker()
      }
      _jLanguage = it
    }

  fun isAvailable() = GrazieRemote.isAvailableLocally(this)

  fun isEnglish() = iso == LanguageISO.EN

  fun equalsTo(language: ai.grazie.nlp.langs.Language) = iso == language.iso

  override fun toString() = displayName
}

