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
  ARABIC(listOf("Arabic"), "13 MB", LanguageISO.AR, "5eab7cc21e943e3da6cac575eea3f91e"),
  ASTURIAN(listOf("Asturian"), "1 MB", LanguageISO.AST, "b6dcf66f6f929433054b45e6e57ee99e"),
  BELARUSIAN(listOf("Belarusian"), "1 MB", LanguageISO.BE, "72bbafcef04680eae38f566e6f7f4f9a"),
  BRETON(listOf("Breton"), "2 MB", LanguageISO.BR, "8efe4295cb8ef527182d1256a20cb28f"),
  CATALAN(listOf("Catalan", "ValencianCatalan"), "4 MB", LanguageISO.CA, "99fc8d7ca3b563df37ce18451ba56518"),
  DANISH(listOf("Danish"), "1 MB", LanguageISO.DA, "af6bbb11e8534925f4214be2c68d3741"),
  GERMAN(listOf("GermanyGerman", "AustrianGerman"), "20 MB", LanguageISO.DE, "353be4a0ebf7dfaf1e9accf19476195a"),
  GREEK(listOf("Greek"), "1 MB", LanguageISO.EL, "3692b14c1d2cef954e977c018119ef7f"),
  ENGLISH(
    listOf("BritishEnglish", "AmericanEnglish", "CanadianEnglish"),
    "16 MB",
    LanguageISO.EN,
    "6f5c7b416678048fad1cc030886412ac"
  ),
  ESPERANTO(listOf("Esperanto"), "1 MB", LanguageISO.EO, "bdb271c47b0850a9bfc51092ef96deea"),
  SPANISH(listOf("Spanish"), "3 MB", LanguageISO.ES, "89ce5fec7eccdb58c64549898b0426c8"),
  PERSIAN(listOf("Persian"), "1 MB", LanguageISO.FA, "373536eba23893362446c29fbbb9b5e3"),
  FRENCH(listOf("French"), "2 MB", LanguageISO.FR, "7c77e250c58c40d188efa12bed34c641"),
  IRISH(listOf("Irish"), "13 MB", LanguageISO.GA, "025b9849f6c1b5d54497808837dee087"),
  GALICIAN(listOf("Galician"), "5 MB", LanguageISO.GL, "0404e28ec74292ab15895256d801bdfc"),
  ITALIAN(listOf("Italian"), "1 MB", LanguageISO.IT, "934abe5a22b88b71b918a1e8b1e4a3e9"),
  JAPANESE(listOf("Japanese"), "21 MB", LanguageISO.JA, "9fcea26ece1416abe9bb840c2f3d6937"),
  KHMER(listOf("Khmer"), "1 MB", LanguageISO.KM, "82ec569297a92710bb6f5b28a7374cce"),
  DUTCH(listOf("Dutch"), "37 MB", LanguageISO.NL, "936ab27dfdd0be3870a81b61c9a47b22"),
  POLISH(listOf("Polish"), "5 MB", LanguageISO.PL, "d0fae544039b222ae2285e498160b2f3"),
  PORTUGUESE(
    listOf("PortugalPortuguese", "BrazilianPortuguese", "AngolaPortuguese", "MozambiquePortuguese"),
    "5 MB",
    LanguageISO.PT,
    "2ad8dfffa23b26ca9e2fb8d4c5a04801"
  ),
  ROMANIAN(listOf("Romanian"), "2 MB", LanguageISO.RO, "3e7ed43cb3e27afe589cd0532be45267"),
  RUSSIAN(listOf("Russian"), "5 MB", LanguageISO.RU, "305d4cae004eeb08448ef0b8dd3fcdaa"),
  SLOVAK(listOf("Slovak"), "3 MB", LanguageISO.SK, "d42eee3a105cdd53114bf885d90c9639"),
  SLOVENIAN(listOf("Slovenian"), "1 MB", LanguageISO.SL, "838b3f596652dd559dafb0328307fcf4"),
  SWEDISH(listOf("Swedish"), "1 MB", LanguageISO.SV, "277cfd5716a4f8feaf4fca8f41b45fa5"),
  TAMIL(listOf("Tamil"), "1 MB", LanguageISO.TA, "53578f2d4d2090032f1432c307efeabe"),
  TAGALOG(listOf("Tagalog"), "1 MB", LanguageISO.TL, "8e07e2c664d6b6a63858b990ecff3645"),
  UKRAINIAN(listOf("Ukrainian"), "7 MB", LanguageISO.UK, "f6371c35ad309c76a0e45fb85b3b8a79"),
  CHINESE(listOf("Chinese"), "8 MB", LanguageISO.ZH, "cb8342bbade8388c36301f3d6a03c9b4");

  val fileName: String by lazy { "$iso-${GraziePlugin.LanguageTool.version}.jar" }
  val file: Path by lazy { GrazieDynamic.dynamicFolder.resolve(fileName) }
  val url: String by lazy { "${GraziePlugin.LanguageTool.url}/${GraziePlugin.LanguageTool.version}/$fileName" }
}
