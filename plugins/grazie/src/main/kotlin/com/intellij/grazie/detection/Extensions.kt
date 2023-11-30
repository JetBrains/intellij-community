package com.intellij.grazie.detection

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.alphabet.Alphabet
import com.intellij.grazie.jlanguage.Lang

fun Lang.toLanguage() = Language.values().find { it.iso == this.iso }!!

/** Note that it will return SOME dialect */
fun Language.toLang() = Lang.values().find { it.iso == this.iso }!!

fun Language.toLangOrNull(): Lang? {
  return Lang.values().find { it.iso == this.iso }
}

val Language.hasWhitespaces: Boolean
  get() = alphabet.group != Alphabet.Group.ASIAN
