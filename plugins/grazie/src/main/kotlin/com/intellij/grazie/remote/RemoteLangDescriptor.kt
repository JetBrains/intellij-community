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
  ARABIC(listOf("Arabic"), "13 MB", LanguageISO.AR, "608c5a35f49ebae55269570f3846ecca"),
  ASTURIAN(listOf("Asturian"), "1 MB", LanguageISO.AST, "4c3cffbd775a6114ac21bb2e3fd0a14c"),
  BELARUSIAN(listOf("Belarusian"), "1 MB", LanguageISO.BE, "da2ac9b848ed37cb950a8bc10ee0c4a5"),
  BRETON(listOf("Breton"), "2 MB", LanguageISO.BR, "bf0210b228ae15ebf6c1011fcffaab29"),
  CATALAN(listOf("Catalan", "ValencianCatalan"), "4 MB", LanguageISO.CA, "2022f39ebdae27293f920c3ad7698982"),
  DANISH(listOf("Danish"), "1 MB", LanguageISO.DA, "86a0378dfdcf414a89288542b4bcb945"),
  GERMAN(listOf("GermanyGerman", "AustrianGerman", "SwissGerman"), "20 MB", LanguageISO.DE, "07011be6808e3b855e27968da47978f2"),
  GREEK(listOf("Greek"), "1 MB", LanguageISO.EL, "37444aaefee6c92c65646e65fe21b398"),
  ENGLISH(
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"),
    "16 MB",
    LanguageISO.EN,
    "2a2a132adb2f4e3ba12fd0b25d1f3627"
  ),
  ESPERANTO(listOf("Esperanto"), "1 MB", LanguageISO.EO, "de29e880c70223529549db868ad65e52"),
  SPANISH(listOf("Spanish"), "3 MB", LanguageISO.ES, "4c076cd683b77a86318cc8a32b1b2aea"),
  PERSIAN(listOf("Persian"), "1 MB", LanguageISO.FA, "d8d64a7b449517b8fd35a4c89194ab8c"),
  FRENCH(listOf("French"), "2 MB", LanguageISO.FR, "7ba53f41f20baa47cd34e8bf05c63e6c"),
  IRISH(listOf("Irish"), "13 MB", LanguageISO.GA, "ce047424b9d7910cda03b16eef5e5ff7"),
  GALICIAN(listOf("Galician"), "5 MB", LanguageISO.GL, "db27321e3e9fac06a46cf823db35b051"),
  ITALIAN(listOf("Italian"), "1 MB", LanguageISO.IT, "b8686ddb908b57b0494d8c00c56125bb"),
  JAPANESE(listOf("Japanese"), "21 MB", LanguageISO.JA, "30d3fcb1feab6905200bce43a2384484"),
  KHMER(listOf("Khmer"), "1 MB", LanguageISO.KM, "27f15fd76dca5cbcdecfe106a9289772"),
  DUTCH(listOf("Dutch"), "37 MB", LanguageISO.NL, "240a880e6b7aceee20a1703a9d181b46"),
  POLISH(listOf("Polish"), "5 MB", LanguageISO.PL, "0d54f1bc8ea20b8cdadcf2e9a5a62833"),
  PORTUGUESE(
    listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"),
    "5 MB",
    LanguageISO.PT,
    "52f831bf3df8b35c764164188e71325c"
  ),
  ROMANIAN(listOf("Romanian"), "2 MB", LanguageISO.RO, "50cd4af06c481971fc09113ef40d4441"),
  RUSSIAN(listOf("Russian"), "5 MB", LanguageISO.RU, "52b83db317ffb6afc5519187db304961"),
  SLOVAK(listOf("Slovak"), "3 MB", LanguageISO.SK, "ce6d03f489d51b674c2b6da3fcefe211"),
  SLOVENIAN(listOf("Slovenian"), "1 MB", LanguageISO.SL, "62f3b059f3b92c40f36dca9fc7fea7d6"),
  SWEDISH(listOf("Swedish"), "1 MB", LanguageISO.SV, "973ecec3c86a9429b401383cb0d372d2"),
  TAMIL(listOf("Tamil"), "1 MB", LanguageISO.TA, "ac8b93170125827aa489b78961f55951"),
  TAGALOG(listOf("Tagalog"), "1 MB", LanguageISO.TL, "2f7965867fdeffb07f27b54c04d0885e"),
  UKRAINIAN(listOf("Ukrainian"), "7 MB", LanguageISO.UK, "7b07df81f37310d96a9d127a95e7afcf"),
  CHINESE(listOf("Chinese"), "8 MB", LanguageISO.ZH, "be4c770b797902d2bd951f0738ae7107");

  val fileName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  val file: Path by lazy { GrazieDynamic.dynamicFolder.resolve(fileName) }
  val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$fileName" }
}
