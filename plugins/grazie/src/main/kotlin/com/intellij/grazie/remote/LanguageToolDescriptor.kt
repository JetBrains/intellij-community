// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.remote

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.grazie.GraziePlugin
import java.nio.file.Path
import kotlin.io.path.Path

// These checksums may be obtained by running the [GrazieLanguageBundleInfoTest]
private const val EN_CHECKSUM = "e8d6689b88d9d58810e6edc1eb4a7105"
private const val AR_CHECKSUM = "224c6b9caed912036cbd3a966c57e5ae"
private const val AST_CHECKSUM = "c43afc5eabefdb3009e995f011196a50"
private const val BE_CHECKSUM = "cc938a3c469b254c124bd2b9a7f37530"
private const val BR_CHECKSUM = "5e71090b9b50cee612a4a972ad231210"
private const val CA_CHECKSUM = "546f2345599e58da15228db7eb2e7629"
private const val DA_CHECKSUM = "fbbc3df19ee5a9bfd87802e87975c588"
private const val DE_CHECKSUM = "3ac0c372a665fd6e93cf7974e19e1410"
private const val EL_CHECKSUM = "a6a57ea4ef35bcea71bc9e601a16ff50"
private const val EO_CHECKSUM = "8e1a11ab0c2a669f98e01c28698a735e"
private const val ES_CHECKSUM = "9d6ff14e4f32dffb36182724e00b328c"
private const val FA_CHECKSUM = "f8307ae6ac5931835f18b4dcb1f0fdf2"
private const val FR_CHECKSUM = "4a94f481437b58018682bc6a3afb80b8"
private const val GA_CHECKSUM = "7cec917d8f3a2ff5209ab2e7f45114a1"
private const val GL_CHECKSUM = "77dc2c28f49f6020cc55c67847ac3dd6"
private const val IT_CHECKSUM = "3384246394ef168eb2b1b33133dd37a3"
private const val JA_CHECKSUM = "1301561f934693e5d6af720fb40e7da8"
private const val KM_CHECKSUM = "1fbd2e2839f0f55a1f3aadeea5c30a80"
private const val NL_CHECKSUM = "67335aa741e28eeb9240ed58601f2c06"
private const val PL_CHECKSUM = "80b420ee734d17d49cd44161900686d6"
private const val PT_CHECKSUM = "f074a6dff5ca136bb58a2459af7047ed"
private const val RO_CHECKSUM = "00364d568765bccfeeb323985e503bac"
private const val RU_CHECKSUM = "66a9eae4c6bdf3b905abd4c63f981699"
private const val SK_CHECKSUM = "f55829f1a0787bb5a0c2616ef6f626dc"
private const val SL_CHECKSUM = "0fb8907a330092f96a4e7c8e8c3fdb99"
private const val SV_CHECKSUM = "2a7640fb469ebaf0e00286010ba198a6"
private const val TA_CHECKSUM = "cb8a371558e03428ea25c8d65f6041bc"
private const val TL_CHECKSUM = "da95f9ec497cc5f451474652c2789781"
private const val UK_CHECKSUM = "428451f61de4c1873919067d0eac4b67"
private const val ZH_CHECKSUM = "33590632112f2bf7a25653f99ea18ad2"

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
