// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detector.model.LanguageISO
import java.nio.file.Path

enum class RemoteLangDescriptor(val langsClasses: List<String>, val size: String, val iso: LanguageISO) {
  ARABIC(listOf("Arabic"), "13 MB", LanguageISO.AR),
  ASTURIAN(listOf("Asturian"), "1 MB", LanguageISO.AST),
  BELARUSIAN(listOf("Belarusian"), "1 MB", LanguageISO.BE),
  BRETON(listOf("Breton"), "2 MB", LanguageISO.BR),
  CATALAN(listOf("Catalan", "ValencianCatalan"), "4 MB", LanguageISO.CA),
  DANISH(listOf("Danish"), "1 MB", LanguageISO.DA),
  GERMAN(listOf("GermanyGerman", "AustrianGerman"), "19 MB", LanguageISO.DE),
  GREEK(listOf("Greek"), "1 MB", LanguageISO.EL),
  ENGLISH(listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"), "15 MB", LanguageISO.EN),
  ESPERANTO(listOf("Esperanto"), "1 MB", LanguageISO.EO),
  SPANISH(listOf("Spanish"), "3 MB", LanguageISO.ES),
  PERSIAN(listOf("Persian"), "1 MB", LanguageISO.FA),
  FRENCH(listOf("French"), "3 MB", LanguageISO.FR),
  IRISH(listOf("Irish"), "13 MB", LanguageISO.GA),
  GALICIAN(listOf("Galician"), "5 MB", LanguageISO.GL),
  ITALIAN(listOf("Italian"), "1 MB", LanguageISO.IT),
  JAPANESE(listOf("Japanese"), "19 MB", LanguageISO.JA),
  KHMER(listOf("Khmer"), "1 MB", LanguageISO.KM),
  DUTCH(listOf("Dutch"), "21 MB", LanguageISO.NL),
  POLISH(listOf("Polish"), "5 MB", LanguageISO.PL),
  PORTUGUESE(listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"), "5 MB", LanguageISO.PT),
  ROMANIAN(listOf("Romanian"), "2 MB", LanguageISO.RO),
  RUSSIAN(listOf("Russian"), "5 MB", LanguageISO.RU),
  SLOVAK(listOf("Slovak"), "3 MB", LanguageISO.SK),
  SLOVENIAN(listOf("Slovenian"), "1 MB", LanguageISO.SL),
  SWEDISH(listOf("Swedish"), "1 MB", LanguageISO.SV),
  TAMIL(listOf("Tamil"), "1 MB", LanguageISO.TA),
  TAGALOG(listOf("Tagalog"), "1 MB", LanguageISO.TL),
  UKRAINIAN(listOf("Ukrainian"), "6 MB", LanguageISO.UK),
  CHINESE(listOf("Chinese"), "8 MB", LanguageISO.ZH);

  val fileName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  val file: Path by lazy { GrazieDynamic.dynamicFolder.resolve(fileName) }
  val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$fileName" }
}
