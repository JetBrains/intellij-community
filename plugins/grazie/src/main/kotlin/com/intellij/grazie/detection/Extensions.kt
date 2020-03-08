package com.intellij.grazie.detection

import com.intellij.grazie.jlanguage.Lang
import tanvd.grazie.langdetect.model.Language
import tanvd.grazie.langdetect.model.alphabet.Alphabet

fun Lang.toLanguage() = Language.values().find { it.iso == this.iso }!!

/** Note that it will return SOME dialect */
fun Language.toLang() = Lang.values().find { it.iso == this.iso }!!

val Language.displayName: String
  get() = this.name.toLowerCase().capitalize()

val Language.hasWhitespaces: Boolean
  get() = alphabet.group != Alphabet.Group.ASIAN
