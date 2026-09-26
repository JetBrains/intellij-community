// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "e7ca9e837d70a0e9d6615740bb645a4a"
private const val AR_CHECKSUM = "ca60d8f37fdb308d1e0ebd6001eb3098"
private const val AST_CHECKSUM = "bd171c69da75a839feddbc9e4df56e0f"
private const val BE_CHECKSUM = "90a977b42466f9f4fddcab9c50c760a6"
private const val BR_CHECKSUM = "7be058a5850351921298b68a0549a170"
private const val CA_CHECKSUM = "ae7efc670c1a949d679a9a5ea77338d6"
private const val DA_CHECKSUM = "73889787f243c0ad357669b021756994"
private const val DE_CHECKSUM = "cf9f3064454732242d1bd5b388d90d83"
private const val EL_CHECKSUM = "c92eddb82ca04a42b17eb3e7b4ad6a8d"
private const val EO_CHECKSUM = "97a3adcc7fea35f9dcad575b06d41b33"
private const val ES_CHECKSUM = "05900331c03a2f5115cb75258499aa26"
private const val FA_CHECKSUM = "72013264afff51f5343b5d6a9788e8a1"
private const val FR_CHECKSUM = "8e7efdd39dc2ff1797e3cce617e12f4c"
private const val GA_CHECKSUM = "886f92c30b0bc901d58281fc9ad7e692"
private const val GL_CHECKSUM = "58b825dad164fa8f7443130de9f3af72"
private const val IT_CHECKSUM = "cec4b5aa619a562e73b6b672c098d8cd"
private const val JA_CHECKSUM = "4e71856e9202a84d6c4ac08971125d61"
private const val KM_CHECKSUM = "f63702267e2b1c8bd76b5672d69c6184"
private const val NL_CHECKSUM = "9bb7d87b5d7b81fa0bd8dc56ea79fd63"
private const val PL_CHECKSUM = "4185e1a1dd45ca0ab020f2811c4ae6ab"
private const val PT_CHECKSUM = "bfa1297eb449f582a60af2421349e426"
private const val RO_CHECKSUM = "c5a27ddf709d0ab06a84c93be0de5a6e"
private const val RU_CHECKSUM = "6bfaba16ea83a185c82fc0c83ee67741"
private const val SK_CHECKSUM = "f950049dc7a3da4e22ee273558c2496c"
private const val SL_CHECKSUM = "63ca7baabf291f4c44ebba649f7d4ea1"
private const val SV_CHECKSUM = "a47b276bdd4e540eb17b0e8cc7e2e3c1"
private const val TA_CHECKSUM = "6f762c7445d6ab69f94fa0a1cd82cdd2"
private const val TL_CHECKSUM = "8f5ac0e65170eb32c6a33cdc95d230b1"
private const val UK_CHECKSUM = "5adefc9d5f684ab6cd4580a2d6054287"
private const val ZH_CHECKSUM = "923eb1034f2602d36019382c1e8e82ff"

enum class LanguageToolDescriptor(
  val langsClasses: List<String>,
  override val size: Int,
  override val iso: LanguageISO,
  override val checksum: String,
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
