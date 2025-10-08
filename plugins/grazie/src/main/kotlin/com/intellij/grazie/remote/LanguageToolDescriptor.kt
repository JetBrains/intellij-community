// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [LanguageToolBundleInfoTest]
private const val EN_CHECKSUM = "7a6223f2dae138ecd5c6b561bef48907"
private const val AR_CHECKSUM = "dd70fada7382cd544483aac53884a358"
private const val AST_CHECKSUM = "3062799e1b446f1fda0c80d750934f87"
private const val BE_CHECKSUM = "9595c74a36fc0121cca3d69e76102a04"
private const val BR_CHECKSUM = "a975c435e98f865b57970220898c5b0b"
private const val CA_CHECKSUM = "692b9563c8679ad2497cd2fd99086b35"
private const val DA_CHECKSUM = "c13a32241638d81b0001b7edd039f87c"
private const val DE_CHECKSUM = "6eea64d4439949a1d3de3b3bd03a1141"
private const val EL_CHECKSUM = "f1275e795153fce8fa5eda7462b91280"
private const val EO_CHECKSUM = "63e939bd01a229d6ed8a0fa46252e83f"
private const val ES_CHECKSUM = "6a2fa26403b0da6b685763fe4ea036ab"
private const val FA_CHECKSUM = "b1f4aee1538cdd58f2a5f844078fbf46"
private const val FR_CHECKSUM = "2c818f5abcc4d2b2873c9677599a3177"
private const val GA_CHECKSUM = "d480fa6c0d1ad63a68750bbe4e79e540"
private const val GL_CHECKSUM = "c90c89cfc0b955aa7520346b35f5a09e"
private const val IT_CHECKSUM = "f38bb43f95c5c595b7060bcb6375bf9d"
private const val JA_CHECKSUM = "e1fda10c4bb050ab880e750a14455d76"
private const val KM_CHECKSUM = "773e92dcc84e39c40e25e118c8c7f691"
private const val NL_CHECKSUM = "a74c6c23a06448dbbc2744a290d115ec"
private const val PL_CHECKSUM = "252bf58eafe1ff315764815cb174fe01"
private const val PT_CHECKSUM = "5fb9bf240e4f74c77ed3cbc230178125"
private const val RO_CHECKSUM = "39c936f8d9ac3ae2798607c960ab48ce"
private const val RU_CHECKSUM = "6faa1d995d38fb8d01a7891b05dfa4e5"
private const val SK_CHECKSUM = "251292cb53688e1da7b47d8ed4cf513b"
private const val SL_CHECKSUM = "456d69c6017a49a9cc52cdafe86e0cf8"
private const val SV_CHECKSUM = "137b521f038fe3b237efb28286963b10"
private const val TA_CHECKSUM = "2b0ea411b684a6a15d3e7e2a322291e9"
private const val TL_CHECKSUM = "9fed940e6ff499b0c817cab89ce074a9"
private const val UK_CHECKSUM = "1ab8a48e669995bbf8c7dad25e4fdc6e"
private const val ZH_CHECKSUM = "ec189d0a0c8706ea623702ad79293897"

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
