// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [GrazieLanguageBundleInfoTest]
private const val EN_CHECKSUM = "c5c124b5a9fa4505c7c5f863248fc122"
private const val AR_CHECKSUM = "50c9c2cfbb58493a0f8d1656fda7eb07"
private const val AST_CHECKSUM = "ce5c099340aa9a972bf7caca9bfe1b7d"
private const val BE_CHECKSUM = "d58d721022b130946a047941e913ad6d"
private const val BR_CHECKSUM = "dc661c66add1bdf161f7c9816f5a41f1"
private const val CA_CHECKSUM = "3226d7cdd5ab6ecc6aa39c1783dd0d7f"
private const val DA_CHECKSUM = "e9238640534a16319603573ee3f6b41d"
private const val DE_CHECKSUM = "e7bb84c1b19e2324613319c3f5d00b4b"
private const val EL_CHECKSUM = "716e4d82553cecb21f25c395e24f4c8a"
private const val EO_CHECKSUM = "ae460e8f91cbbbdf0fcd8fae08848a2a"
private const val ES_CHECKSUM = "daf10efdcded9bc5d52c1a950c6e27c6"
private const val FA_CHECKSUM = "d4451fe91cbe75b30b5e76cb78092593"
private const val FR_CHECKSUM = "4d73bd9cf8cfcea3fbe41e669b9a205e"
private const val GA_CHECKSUM = "82dcbf4e47a702472b9564c50669fedb"
private const val GL_CHECKSUM = "b5e3ca8c87e796951b6c8023ec7c90ad"
private const val IT_CHECKSUM = "e7594285e6dbed74bd2d90e7fd33ba0f"
private const val JA_CHECKSUM = "96f9a6221a3c9b17d3f819e48913697e"
private const val KM_CHECKSUM = "f8bd0cdd22944d0c7d26605166789f53"
private const val NL_CHECKSUM = "e15b53f374359eb5b4a672a32a02681f"
private const val PL_CHECKSUM = "0f39a2dcf10789ee11b82f8b3aaf173f"
private const val PT_CHECKSUM = "cdd7941c3ea29f3b4eb858e402dc4907"
private const val RO_CHECKSUM = "8fb10c54d46b5fc5ed7a286e68a4a21e"
private const val RU_CHECKSUM = "9c28786ad1d92416901a73585a5d57d7"
private const val SK_CHECKSUM = "85c30c77c81b2bb1111755acfd609317"
private const val SL_CHECKSUM = "5418b68501024fce8cd319f60ceea60f"
private const val SV_CHECKSUM = "db820953d6faaf42a30081c2b85f01e3"
private const val TA_CHECKSUM = "dfa394beb0e0a60010e37454bf08a94d"
private const val TL_CHECKSUM = "373b225312854b4c10daa900651d5312"
private const val UK_CHECKSUM = "25ff7be57c1531f30a5649494fa24ef7"
private const val ZH_CHECKSUM = "ef3930ed16f60a228ec483ce30c5cc8f"

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
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"),
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
