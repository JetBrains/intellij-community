// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "89b1981b2e49fad5d338c89af4d166e6"
private const val AR_CHECKSUM = "3cb578c2c57d6c2162896dc3f0382597"
private const val AST_CHECKSUM = "b9705acc5419b3009dc2be01ae76d80b"
private const val BE_CHECKSUM = "99cab8879dae9ac5e0bb6228128c8476"
private const val BR_CHECKSUM = "be528bef4282f7966390845387d763c5"
private const val CA_CHECKSUM = "6f0c37426b8c4079f273a2d6e00918a4"
private const val DA_CHECKSUM = "48f1e4417e759e7f6949b0738a507440"
private const val DE_CHECKSUM = "4e3faf2eb9f30fc695e7456e8e3e7d36"
private const val EL_CHECKSUM = "6cb51c0770702ddddf9f7c4e98a5b025"
private const val EO_CHECKSUM = "afdf8d3541dbc09481ae5cc1e9a02c50"
private const val ES_CHECKSUM = "cbf994dcb79a06711a9b1e1de47719d6"
private const val FA_CHECKSUM = "a1ce1d0bcbe72ed39e79b403f2110c58"
private const val FR_CHECKSUM = "929cd090d4c65a859735000c6b1b6068"
private const val GA_CHECKSUM = "ef61b838971af4e9d548e8db9b438d26"
private const val GL_CHECKSUM = "c58d6152d89dfba2a39daab776bd2bda"
private const val IT_CHECKSUM = "a6935222a0af957bf674cc68064e3084"
private const val JA_CHECKSUM = "7f01bbf2bf1308badefc7271ca1b7e64"
private const val KM_CHECKSUM = "0e4352d979d33b61b1f46ee02bfde796"
private const val NL_CHECKSUM = "5e58b91d81975a1018d5bba14870e0fb"
private const val PL_CHECKSUM = "ebcd20718502aad48f3e19d26cfc099c"
private const val PT_CHECKSUM = "5eb5079b7b263729b381fa59f5d1eab3"
private const val RO_CHECKSUM = "6bd6739db60e79805830775c14cc8d60"
private const val RU_CHECKSUM = "9c8931df1c686f7b396b3b31898c67ae"
private const val SK_CHECKSUM = "b269341f1b88403e1f975c6cfd03b5c7"
private const val SL_CHECKSUM = "25d7ed8a5622e12114a6fad54a7ed197"
private const val SV_CHECKSUM = "8b9c02691791dd1ba3d4053c79c385ab"
private const val TA_CHECKSUM = "fdf507c4b9c1859b262daf8b4177ad2f"
private const val TL_CHECKSUM = "867fd0cc605bd3b887ee24824d6fa629"
private const val UK_CHECKSUM = "691c2c7d6950bedbc8fd25abff6f9db6"
private const val ZH_CHECKSUM = "dd8aff9d985d006052773402362c80ae"

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
