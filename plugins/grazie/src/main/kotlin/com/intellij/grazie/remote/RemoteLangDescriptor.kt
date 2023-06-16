// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path

@Suppress("SpellCheckingInspection")
enum class RemoteLangDescriptor(
  val langsClasses: List<String>,
  val size: String,
  val iso: LanguageISO,
  val checksum: String
) {
  ARABIC(listOf("Arabic"), "13 MB", LanguageISO.AR, "27cccd62b06e4b6246665b9788470d04"),
  ASTURIAN(listOf("Asturian"), "1 MB", LanguageISO.AST, "c4edcb3fb07372b25241213287a87f54"),
  BELARUSIAN(listOf("Belarusian"), "1 MB", LanguageISO.BE, "9ec35a14bd1f97d82183cb956588070a"),
  BRETON(listOf("Breton"), "2 MB", LanguageISO.BR, "2bc3b9a59b606dc9e65dc1aff41f3b99"),
  CATALAN(listOf("Catalan", "ValencianCatalan"), "4 MB", LanguageISO.CA, "f0c77d15096ca5f612970b266dfd9be5"),
  DANISH(listOf("Danish"), "1 MB", LanguageISO.DA, "0040958245a2d78e8b97566440a2c52c"),
  GERMAN(listOf("GermanyGerman", "AustrianGerman"), "20 MB", LanguageISO.DE, "fbb5fba1a430bcbb6c6ca1d41b335e5f"),
  GREEK(listOf("Greek"), "1 MB", LanguageISO.EL, "1a91dcdc83b3617312267e336e772f2c"),
  ENGLISH(
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"),
    "16 MB",
    LanguageISO.EN,
    "8d70c0b9d60b176099d1030230ef6260"
  ),
  ESPERANTO(listOf("Esperanto"), "1 MB", LanguageISO.EO, "d4223c32c39159afccb59397bb8be36f"),
  SPANISH(listOf("Spanish"), "3 MB", LanguageISO.ES, "5f1a993568861ad4520e1317736b3462"),
  PERSIAN(listOf("Persian"), "1 MB", LanguageISO.FA, "a2d719d88b2b59cc58675d48855cd44d"),
  FRENCH(listOf("French"), "2 MB", LanguageISO.FR, "6a9d83d0e8c7e722387c7e5c421403d0"),
  IRISH(listOf("Irish"), "13 MB", LanguageISO.GA, "35356a9f1ea4f0805432acd33c607308"),
  GALICIAN(listOf("Galician"), "5 MB", LanguageISO.GL, "566f76c2f6b715a00025d7e7bef4a0e2"),
  ITALIAN(listOf("Italian"), "1 MB", LanguageISO.IT, "fd19a8d2951e3bb70d5949cbc2dde632"),
  JAPANESE(listOf("Japanese"), "21 MB", LanguageISO.JA, "1d4fb56f6da89f3f4488647a7d7028b1"),
  KHMER(listOf("Khmer"), "1 MB", LanguageISO.KM, "b7ba0e82ed00594e55aedfd1a27eeefa"),
  DUTCH(listOf("Dutch"), "37 MB", LanguageISO.NL, "091f012ef54fde46d6b513a4654ad924"),
  POLISH(listOf("Polish"), "5 MB", LanguageISO.PL, "8406217a09a431ba9d54f4ed569139f8"),
  PORTUGUESE(
    listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"),
    "5 MB",
    LanguageISO.PT,
    "6c4825ed2204a54dfa50f60dfb370f1e"
  ),
  ROMANIAN(listOf("Romanian"), "2 MB", LanguageISO.RO, "23f06a190d9f19ed6fb16a19ba9c8223"),
  RUSSIAN(listOf("Russian"), "5 MB", LanguageISO.RU, "03d7e3b79c3b422a05d3c110a9c01762"),
  SLOVAK(listOf("Slovak"), "3 MB", LanguageISO.SK, "d5292fe2f238c2704e02c5c418ec4c9e"),
  SLOVENIAN(listOf("Slovenian"), "1 MB", LanguageISO.SL, "498334f310144e59a308611f67f470c3"),
  SWEDISH(listOf("Swedish"), "1 MB", LanguageISO.SV, "4b3c78f4c6c11b551242bee6967681a3"),
  TAMIL(listOf("Tamil"), "1 MB", LanguageISO.TA, "b5e7583bda619afa7db75204690e8b71"),
  TAGALOG(listOf("Tagalog"), "1 MB", LanguageISO.TL, "bc65902c3df92ba1113c24dd73bef95f"),
  UKRAINIAN(listOf("Ukrainian"), "7 MB", LanguageISO.UK, "f69b93e79163eb517a8757b562c3e2f5"),
  CHINESE(listOf("Chinese"), "8 MB", LanguageISO.ZH, "9a248a42c8903f4e31989bd72726af69");

  val fileName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  val file: Path by lazy { GrazieDynamic.dynamicFolder.resolve(fileName) }
  val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$fileName" }
}
