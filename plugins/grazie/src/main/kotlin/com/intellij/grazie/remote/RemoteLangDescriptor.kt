// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detector.model.LanguageISO
import java.nio.file.Path

internal enum class RemoteLangDescriptor(val langsClasses: List<String>, val size: String, val iso: LanguageISO) {
  ENGLISH(listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"), "14 MB", LanguageISO.EN),
  RUSSIAN(listOf("Russian"), "3 MB", LanguageISO.RU),
  PERSIAN(listOf("Persian"), "1 MB", LanguageISO.FA),
  FRENCH(listOf("French"), "4 MB", LanguageISO.FR),
  GERMAN(listOf("GermanyGerman", "AustrianGerman"), "19 MB", LanguageISO.DE),
  POLISH(listOf("Polish"), "5 MB", LanguageISO.PL),
  ITALIAN(listOf("Italian"), "1 MB", LanguageISO.IT),
  DUTCH(listOf("Dutch"), "17 MB", LanguageISO.NL),
  PORTUGUESE(listOf("PortugalPortuguese", "BrazilianPortuguese"), "5 MB", LanguageISO.PT),
  CHINESE(listOf("Chinese"), "3 MB", LanguageISO.ZH),
  GREEK(listOf("Greek"), "1 MB", LanguageISO.EL),
  JAPANESE(listOf("Japanese"), "1 MB", LanguageISO.JA),
  ROMANIAN(listOf("Romanian"), "1 MB", LanguageISO.RO),
  SLOVAK(listOf("Slovak"), "3 MB", LanguageISO.SK),
  SPANISH(listOf("Spanish"), "2 MB", LanguageISO.ES),
  UKRAINIAN(listOf("Ukrainian"), "6 MB", LanguageISO.UK);

  val fileName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  val file: Path by lazy { GrazieDynamic.dynamicFolder.resolve(fileName) }
  val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$fileName" }
}
