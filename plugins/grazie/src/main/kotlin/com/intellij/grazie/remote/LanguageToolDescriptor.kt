// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "7f83bcdc51d0488a390a230394440d3f"
private const val AR_CHECKSUM = "75adfc43106ba78e53cfae407495376e"
private const val AST_CHECKSUM = "6cf5eaff22967400c0f81d89743fd42f"
private const val BE_CHECKSUM = "3673f03628f7211f305475347b4dfd3e"
private const val BR_CHECKSUM = "cd485040dd610a2a0de5dd4eda8f1add"
private const val CA_CHECKSUM = "b2b628d4857d30750a8bfcf5d883aec1"
private const val DA_CHECKSUM = "f24f4158756937673a277b65004aef81"
private const val DE_CHECKSUM = "706709b4170e01c9bc2ddc3771bd667e"
private const val EL_CHECKSUM = "c3f77d3665027b0622225451601bba40"
private const val EO_CHECKSUM = "2beffc8d859453b6f33c6c0e60691ecf"
private const val ES_CHECKSUM = "bc58f002895e88f21402819f9b4ee559"
private const val FA_CHECKSUM = "2528fcfe38334d355c4dda9ba07c0416"
private const val FR_CHECKSUM = "77e32203161d65acd6b2f4f814a83842"
private const val GA_CHECKSUM = "b4fa9b32336f99f6711066e938ad78fc"
private const val GL_CHECKSUM = "26f365ca3eba8c2eef850403410c4bae"
private const val IT_CHECKSUM = "78e7cf0d07356940ea469c568bc945c5"
private const val JA_CHECKSUM = "70bdc49628ee1ef4b28d295ca80980f1"
private const val KM_CHECKSUM = "d48ca35d72bbb44155513858f035e165"
private const val NL_CHECKSUM = "d4994461576c401f1ed4fa560711a884"
private const val PL_CHECKSUM = "1163b0cc332680dc75762b377ec61212"
private const val PT_CHECKSUM = "cf619a9085dc78f8ab348eaf9362a355"
private const val RO_CHECKSUM = "77f3cb9715ec6e8a639547ad035a80d2"
private const val RU_CHECKSUM = "e7f7832a9d8ec6e539c03bcbde1247eb"
private const val SK_CHECKSUM = "b8fa1c2cb07cd29b09dc7c8ea163af93"
private const val SL_CHECKSUM = "c4f08063a2a3aedcde07abccf734e094"
private const val SV_CHECKSUM = "b3737055eb0906ca7604c0235c2daaa0"
private const val TA_CHECKSUM = "46ab025f2a7c56f387078a6c1f777e72"
private const val TL_CHECKSUM = "1b51868d8c0a8bde2f1ffab06482e12e"
private const val UK_CHECKSUM = "c8084dd8a7dcdfa4cc11314ecc052652"
private const val ZH_CHECKSUM = "baf4ebcbf02af32d52985ac9fa95d5b6"

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
