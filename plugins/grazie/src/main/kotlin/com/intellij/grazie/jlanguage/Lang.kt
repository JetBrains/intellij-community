// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.jlanguage

import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.nlp.langs.LanguageWithVariant
import ai.grazie.spell.utils.DictionaryResources.transformSwissGermanDic
import ai.grazie.spell.utils.DictionaryResources.transformSwissGermanTrigrams
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GrazieDynamic.getLangDynamicFolder
import com.intellij.grazie.remote.GrazieRemote
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.grazie.remote.LanguageToolDescriptor
import com.intellij.grazie.remote.RemoteLangDescriptor
import com.intellij.openapi.util.NlsSafe
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.spellchecker.hunspell.HunspellDictionary
import org.jetbrains.annotations.NonNls
import org.languagetool.Language
import org.languagetool.language.English
import org.languagetool.noop.NoopChunker

enum class Lang(val displayName: String, val className: String, val iso: LanguageISO, @NlsSafe val nativeName: String, @NonNls val variant: String? = null) {
  BRITISH_ENGLISH("English (GB)", "BritishEnglish", LanguageISO.EN, "English (Great Britain)"),
  AMERICAN_ENGLISH("English (US)", "AmericanEnglish", LanguageISO.EN, "English (USA)"),
  CANADIAN_ENGLISH("English (Canadian)", "CanadianEnglish", LanguageISO.EN, "English (Canada)"),
  ARABIC("Arabic", "Arabic", LanguageISO.AR, "العربيةُ"),
  ASTURIAN("Asturian", "Asturian", LanguageISO.AST, "Asturianu"),
  BELARUSIAN("Belarusian", "Belarusian", LanguageISO.BE, "Беларуская"),
  BRETON("Breton", "Breton", LanguageISO.BR, "Brezhoneg"),
  CATALAN("Catalan", "Catalan", LanguageISO.CA, "Català"),
  VALENCIAN_CATALAN("Catalan (Valencian)", "ValencianCatalan", LanguageISO.CA, "Català (Valencià)"),
  DANISH("Danish", "Danish", LanguageISO.DA, "Dansk"),
  GERMANY_GERMAN("German (Germany)", "GermanyGerman", LanguageISO.DE, "Deutsch (Deutschland)"),
  AUSTRIAN_GERMAN("German (Austria)", "AustrianGerman", LanguageISO.DE, "Deutsch (Österreich)"),
  SWISS_GERMAN("German (Switzerland)", "SwissGerman", LanguageISO.DE, "Deutsch (Die Schweiz)", "CH"),
  GREEK("Greek", "Greek", LanguageISO.EL, "Ελληνικά"),
  ESPERANTO("Esperanto", "Esperanto", LanguageISO.EO, "Esperanto"),
  SPANISH("Spanish", "Spanish", LanguageISO.ES, "Español"),
  PERSIAN("Persian", "Persian", LanguageISO.FA, "فارسی"),
  FRENCH("French", "French", LanguageISO.FR, "Français"),
  IRISH("Irish", "Irish", LanguageISO.GA, "Gaeilge"),
  GALICIAN("Galician", "Galician", LanguageISO.GL, "Galego"),
  ITALIAN("Italian", "Italian", LanguageISO.IT, "Italiano"),
  JAPANESE("Japanese", "Japanese", LanguageISO.JA, "日本語"),
  KHMER("Khmer", "Khmer", LanguageISO.KM, "ភាសាខ្មែរ"),
  DUTCH("Dutch", "Dutch", LanguageISO.NL, "Nederlands"),
  POLISH("Polish", "Polish", LanguageISO.PL, "Polski"),
  PORTUGAL_PORTUGUESE("Portuguese (Portugal)", "PortugalPortuguese", LanguageISO.PT, "Português (Portugal)"),
  BRAZILIAN_PORTUGUESE("Portuguese (Brazil)", "BrazilianPortuguese", LanguageISO.PT, "Português (Brasil)"),
  ANGOLA_PORTUGUESE("Portuguese (Angola)", "AngolaPortuguese", LanguageISO.PT, "Português (Angola)"),
  MOZAMBIQUE_PORTUGUESE("Portuguese (Mozambique)", "MozambiquePortuguese", LanguageISO.PT, "Português (Moçambique)"),
  ROMANIAN("Romanian", "Romanian", LanguageISO.RO, "Română"),
  RUSSIAN("Russian", "Russian", LanguageISO.RU, "Русский"),
  SLOVAK("Slovak", "Slovak", LanguageISO.SK, "Slovenčina"),
  SLOVENIAN("Slovenian", "Slovenian", LanguageISO.SL, "Slovenščina"),
  SWEDISH("Swedish", "Swedish", LanguageISO.SV, "Svenska"),
  TAMIL("Tamil", "Tamil", LanguageISO.TA, "தமிழ்"),
  TAGALOG("Tagalog", "Tagalog", LanguageISO.TL, "Tagalog"),
  UKRAINIAN("Ukrainian", "Ukrainian", LanguageISO.UK, "Українська"),
  CHINESE("Chinese", "Chinese", LanguageISO.ZH, "中文");

  companion object {
    fun sortedValues(): List<Lang> = entries.sortedBy(Lang::nativeName)

    // the chunker can be very memory-, disk- and CPU-expensive
    internal fun shouldDisableChunker(language: Language): Boolean = language is English
  }

  val ltRemote: LanguageToolDescriptor?
    get() = LanguageToolDescriptor.entries.find { it.iso == iso }

  val hunspellRemote: HunspellDescriptor?
    get() = HunspellDescriptor.entries.find { it.iso == iso }

  val dictionary: HunspellDictionary? by lazy {
    if (isEnglish()) return@lazy GrazieSpellCheckerEngine.enDictionary
    if (hunspellRemote == null) return@lazy null

    val dicPath = getLangDynamicFolder(this).resolve(hunspellRemote!!.file).toString()
    if (this == SWISS_GERMAN) createSwissDictionary(dicPath) else HunspellDictionary(dicPath, language = iso)
  }

  val withVariant: LanguageWithVariant?
    get() {
      val language = ai.grazie.nlp.langs.Language.entries.find { it.iso == this.iso } ?: return null
      return LanguageWithVariant(language, this.variant)
    }

  val remoteDescriptors: List<RemoteLangDescriptor>
    get() = listOfNotNull(ltRemote, hunspellRemote)

  val size: Int
    get() = (LanguageToolDescriptor.entries.find { it.iso == iso }?.size ?: 0) +
            (HunspellDescriptor.entries.find { it.iso == iso }?.size ?: 0)

  val shortDisplayName: String
    get() {
      if (iso == LanguageISO.DE) return "German"
      if (iso == LanguageISO.EN) return "English"
      if (iso == LanguageISO.PT) return "Portuguese"
      return displayName
    }

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

private fun createSwissDictionary(path: String): HunspellDictionary {
  val (dic, aff, trigrams, replacingRules) = HunspellDictionary.getHunspellBundle(path)
  return HunspellDictionary(
    transformSwissGermanDic(dic.readText()),
    aff.readText(),
    transformSwissGermanTrigrams(trigrams.readLines()),
    path,
    LanguageISO.DE,
    replacingRules?.readText()
  )
}
