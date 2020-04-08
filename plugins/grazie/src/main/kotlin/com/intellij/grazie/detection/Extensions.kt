package com.intellij.grazie.detection

import com.intellij.grazie.detector.model.Language
import com.intellij.grazie.detector.model.alphabet.Alphabet
import com.intellij.grazie.jlanguage.Lang

fun Lang.toLanguage() = Language.values().find { it.iso == this.iso }!!

/** Note that it will return SOME dialect */
fun Language.toLang() = Lang.values().find { it.iso == this.iso }!!

val Language.hasWhitespaces: Boolean
  get() = alphabet.group != Alphabet.Group.ASIAN
