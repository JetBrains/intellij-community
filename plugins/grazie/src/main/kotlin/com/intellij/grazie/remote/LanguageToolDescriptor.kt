// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "f28fffcc56940fc16c09c0eecf0c3075"
private const val AR_CHECKSUM = "76d8a89b82d2ff89e5eb618ac4e1fd32"
private const val AST_CHECKSUM = "82a1313b03f8dad77eee9ee8ef92fe6b"
private const val BE_CHECKSUM = "6bd01b047e384d7d06681d1dcbfd5047"
private const val BR_CHECKSUM = "4abe80b539d1b85098fd04ea16be45a8"
private const val CA_CHECKSUM = "bfb325538540296a0581fd68e0259bac"
private const val DA_CHECKSUM = "5179df8338841a4f90405a084c72dfb1"
private const val DE_CHECKSUM = "bb2e39791fd4625fc3d1fbf77bed13a8"
private const val EL_CHECKSUM = "5de528913cc249144d6bd97ce7a4163a"
private const val EO_CHECKSUM = "9ccd5a61af03a3d5df9741c78a46657f"
private const val ES_CHECKSUM = "a298d602237a61e589269625ff3dca8b"
private const val FA_CHECKSUM = "a9289a824a7aea7f3625343e32f3f1e4"
private const val FR_CHECKSUM = "f1e2595b1c33bdac58c1ffdf6505b5a8"
private const val GA_CHECKSUM = "0dfd2492d9194a87a08e4dc83e8b7cbf"
private const val GL_CHECKSUM = "06487e79a4462def4c57b117b397c8f3"
private const val IT_CHECKSUM = "fa1caf73d4ed5269bc17e8125cabd733"
private const val JA_CHECKSUM = "b5a5436c6e376708e1d6c85d90f7e712"
private const val KM_CHECKSUM = "841339951b8b2aa34e207b243eab5cce"
private const val NL_CHECKSUM = "cfd303c1e351972a9af87f49aadaabba"
private const val PL_CHECKSUM = "52bd0eecec2d402c640d6d9e5f92214a"
private const val PT_CHECKSUM = "92202ab1b1e805960dcbdb3ab9d7cdea"
private const val RO_CHECKSUM = "dd53ef38173ae11a6d456e95596db4d6"
private const val RU_CHECKSUM = "7249590c1869d4fdf88644c197b6273d"
private const val SK_CHECKSUM = "438e9358408aa030ac3b7ee91ed7b7d2"
private const val SL_CHECKSUM = "07b30b188f5200fc5b313a069294b03b"
private const val SV_CHECKSUM = "ab0bf98e78491e5b7ae9b2b988cc4515"
private const val TA_CHECKSUM = "eb6d7f8f5ecf5a1ab8705d9bc70e3eab"
private const val TL_CHECKSUM = "4b82afabb4100591262abee4163eca0a"
private const val UK_CHECKSUM = "6bf7fe211a882c610d9e11acd586a164"
private const val ZH_CHECKSUM = "73fd40d4dbd5315caa727b1ffe68705f"

enum class LanguageToolDescriptor(
  val langsClasses: List<String>,
  override val size: Int,
  override val iso: LanguageISO,
  val checksum: String,
) : RemoteLangDescriptor {
  ARABIC(listOf("Arabic"), 13, LanguageISO.AR, AR_CHECKSUM),
  ASTURIAN(listOf("Asturian"), 1, LanguageISO.AST, AST_CHECKSUM),
  BELARUSIAN(listOf("Belarusian"), 1, LanguageISO.BE, BE_CHECKSUM),
  BRETON(listOf("Breton"), 2, LanguageISO.BR, BR_CHECKSUM),
  CATALAN(listOf("Catalan", "ValencianCatalan", "BalearicCatalan"), 4, LanguageISO.CA, CA_CHECKSUM),
  DANISH(listOf("Danish"), 1, LanguageISO.DA, DA_CHECKSUM),
  GERMAN(listOf("GermanyGerman", "AustrianGerman", "SwissGerman"), 20, LanguageISO.DE, DE_CHECKSUM),
  GREEK(listOf("Greek"), 1, LanguageISO.EL, EL_CHECKSUM),
  ENGLISH(
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish", "AustralianEnglish"),
    16,
    LanguageISO.EN,
    EN_CHECKSUM
  ),
  ESPERANTO(listOf("Esperanto"), 1, LanguageISO.EO, EO_CHECKSUM),
  SPANISH(listOf("Spanish"), 3, LanguageISO.ES, ES_CHECKSUM),
  PERSIAN(listOf("Persian"), 1, LanguageISO.FA, FA_CHECKSUM),
  FRENCH(listOf("French"), 2, LanguageISO.FR, FR_CHECKSUM),
  IRISH(listOf("Irish"), 13, LanguageISO.GA, GA_CHECKSUM),
  GALICIAN(listOf("Galician"), 5, LanguageISO.GL, GL_CHECKSUM),
  ITALIAN(listOf("Italian"), 1, LanguageISO.IT, IT_CHECKSUM),
  JAPANESE(listOf("Japanese"), 21, LanguageISO.JA, JA_CHECKSUM),
  KHMER(listOf("Khmer"), 1, LanguageISO.KM, KM_CHECKSUM),
  DUTCH(listOf("Dutch"), 37, LanguageISO.NL, NL_CHECKSUM),
  POLISH(listOf("Polish"), 5, LanguageISO.PL, PL_CHECKSUM),
  PORTUGUESE(
    listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"),
    5,
    LanguageISO.PT,
    PT_CHECKSUM
  ),
  ROMANIAN(listOf("Romanian"), 2, LanguageISO.RO, RO_CHECKSUM),
  RUSSIAN(listOf("Russian"), 5, LanguageISO.RU, RU_CHECKSUM),
  SLOVAK(listOf("Slovak"), 3, LanguageISO.SK, SK_CHECKSUM),
  SLOVENIAN(listOf("Slovenian"), 1, LanguageISO.SL, SL_CHECKSUM),
  SWEDISH(listOf("Swedish"), 1, LanguageISO.SV, SV_CHECKSUM),
  TAMIL(listOf("Tamil"), 1, LanguageISO.TA, TA_CHECKSUM),
  TAGALOG(listOf("Tagalog"), 1, LanguageISO.TL, TL_CHECKSUM),
  UKRAINIAN(listOf("Ukrainian"), 7, LanguageISO.UK, UK_CHECKSUM),
  CHINESE(listOf("Chinese"), 8, LanguageISO.ZH, ZH_CHECKSUM);

  override val storageName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  override val file: Path by lazy { Path(storageName) }
  override val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$storageName" }
}
