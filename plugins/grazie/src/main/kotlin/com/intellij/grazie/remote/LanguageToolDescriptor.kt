// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [GrazieLanguageBundleInfoTest]
private const val EN_CHECKSUM = "2e9203fae17a9ddb06d8d3c938839b1f"
private const val AR_CHECKSUM = "198102ddb7fb0e82f55b614f5331698c"
private const val AST_CHECKSUM = "035b1fd0300ac8bb9f3cd92f5c4d6053"
private const val BE_CHECKSUM = "9ae62f0a3ae58bd15fe0805f7b290ded"
private const val BR_CHECKSUM = "d67702483b096aed7c84aff58d2a55e5"
private const val CA_CHECKSUM = "c0dc683196d4226eb9b3bd9012efe1e8"
private const val DA_CHECKSUM = "f766ddbc63fbdff8c2d618f49ee509ae"
private const val DE_CHECKSUM = "5f3fd4ea163929b91d31ea5ae4babdf9"
private const val EL_CHECKSUM = "76f45bc5d5a10e24d6e440b5be2e60df"
private const val EO_CHECKSUM = "c0881778c4ae4fc295bd219ed4faadf1"
private const val ES_CHECKSUM = "7ca45b7b076711fdfa72416896689b49"
private const val FA_CHECKSUM = "94f48d55ab4b77a958602dbce92d6c40"
private const val FR_CHECKSUM = "7390d20a793b7edb10a8e2ead955a670"
private const val GA_CHECKSUM = "493ef9c451837be23389dd2a4e26995a"
private const val GL_CHECKSUM = "00d3a0852d5225af6d6eb3aa5472c602"
private const val IT_CHECKSUM = "9146dadc62e152c52b18e3cca83858e9"
private const val JA_CHECKSUM = "1aa2218dc68d22311ae18d851644e1c9"
private const val KM_CHECKSUM = "9405a60231b04f9087eec486bd3ac7d0"
private const val NL_CHECKSUM = "5b4f8c7a96855387f92a9e7be3550d89"
private const val PL_CHECKSUM = "50ddc50f9c7501587ee883c07f8643ee"
private const val PT_CHECKSUM = "eed01398595ae36eeec12a6a9191c481"
private const val RO_CHECKSUM = "0ff1d368c6955c2f3f95c92237db8047"
private const val RU_CHECKSUM = "09c67dd65f8504ee1a06409f4dddc1be"
private const val SK_CHECKSUM = "f74caebc32f8788293aa35315d6848a5"
private const val SL_CHECKSUM = "87472cb969d1c7f5c94ffbb54b1a7584"
private const val SV_CHECKSUM = "ae6bbe4b002e52b92ecafe92bf3b746d"
private const val TA_CHECKSUM = "d07ce086e403c27139dd4c2b1b4845bf"
private const val TL_CHECKSUM = "71ddfc04ddfd6db883cd11def056160d"
private const val UK_CHECKSUM = "e0c03bb65f8fc56188f99b336a5c4b64"
private const val ZH_CHECKSUM = "2eecd6b4301566d6cd1f1d5b14f9f7e3"

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
