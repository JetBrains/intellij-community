// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

private val OTHER_ONLY = setOf("other")
private val ONE_OTHER = setOf("one", "other")
private val ONE_FEW_OTHER = setOf("one", "few", "other")
private val ONE_MANY_OTHER = setOf("one", "many", "other")
private val ONE_TWO_OTHER = setOf("one", "two", "other")
private val ZERO_ONE_OTHER = setOf("zero", "one", "other")
private val ONE_FEW_MANY_OTHER = setOf("one", "few", "many", "other")
private val ONE_TWO_FEW_OTHER = setOf("one", "two", "few", "other")
private val ONE_TWO_FEW_MANY_OTHER = setOf("one", "two", "few", "many", "other")
private val ALL_SIX = setOf("zero", "one", "two", "few", "many", "other")

/**
 * Plural categories per language, generated from the ICU4J 78.3 (CLDR 48) plural rules, sorted by language code.
 * The table covers every language in the CLDR plurals data.
 * It also keeps the ICU-known languages without own rules, which resolve to the root rule with the `other` category only.
 *
 * @see <a href="https://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">CLDR Language Plural Rules</a>
 */
internal val LANGUAGE_PLURAL_CATEGORIES: Map<String, Set<String>> = mapOf(
  "af" to ONE_OTHER, "agq" to OTHER_ONLY, "ak" to ONE_OTHER, "am" to ONE_OTHER, "an" to ONE_OTHER, "ar" to ALL_SIX,
  "ars" to ALL_SIX, "as" to ONE_OTHER, "asa" to ONE_OTHER, "ast" to ONE_OTHER, "az" to ONE_OTHER, "ba" to OTHER_ONLY,
  "bal" to ONE_OTHER, "bas" to OTHER_ONLY, "be" to ONE_FEW_MANY_OTHER, "bem" to ONE_OTHER, "bez" to ONE_OTHER, "bg" to ONE_OTHER,
  "bgc" to OTHER_ONLY, "bho" to ONE_OTHER, "blo" to ZERO_ONE_OTHER, "bm" to OTHER_ONLY, "bn" to ONE_OTHER, "bo" to OTHER_ONLY,
  "br" to ONE_TWO_FEW_MANY_OTHER, "brx" to ONE_OTHER, "bs" to ONE_FEW_OTHER, "bua" to OTHER_ONLY, "ca" to ONE_MANY_OTHER,
  "ccp" to OTHER_ONLY, "ce" to ONE_OTHER, "ceb" to ONE_OTHER, "cgg" to ONE_OTHER, "chr" to ONE_OTHER, "ckb" to ONE_OTHER,
  "cs" to ONE_FEW_MANY_OTHER, "csw" to ONE_OTHER, "cv" to ZERO_ONE_OTHER, "cy" to ALL_SIX, "da" to ONE_OTHER,
  "dav" to OTHER_ONLY, "de" to ONE_OTHER, "dje" to OTHER_ONLY, "doi" to ONE_OTHER, "dsb" to ONE_TWO_FEW_OTHER,
  "dua" to OTHER_ONLY, "dv" to ONE_OTHER, "dyo" to OTHER_ONLY, "dz" to OTHER_ONLY, "ebu" to OTHER_ONLY, "ee" to ONE_OTHER,
  "el" to ONE_OTHER, "en" to ONE_OTHER, "eo" to ONE_OTHER, "es" to ONE_MANY_OTHER, "et" to ONE_OTHER, "eu" to ONE_OTHER,
  "ewo" to OTHER_ONLY, "fa" to ONE_OTHER, "ff" to ONE_OTHER, "fi" to ONE_OTHER, "fil" to ONE_OTHER, "fo" to ONE_OTHER,
  "fr" to ONE_MANY_OTHER, "fur" to ONE_OTHER, "fy" to ONE_OTHER, "ga" to ONE_TWO_FEW_MANY_OTHER, "gaa" to OTHER_ONLY,
  "gd" to ONE_TWO_FEW_OTHER, "gl" to ONE_OTHER, "gsw" to ONE_OTHER, "gu" to ONE_OTHER, "guw" to ONE_OTHER, "guz" to OTHER_ONLY,
  "gv" to ONE_TWO_FEW_MANY_OTHER, "ha" to ONE_OTHER, "haw" to ONE_OTHER, "he" to ONE_TWO_OTHER, "hi" to ONE_OTHER,
  "hnj" to OTHER_ONLY, "hr" to ONE_FEW_OTHER, "hsb" to ONE_TWO_FEW_OTHER, "hu" to ONE_OTHER, "hy" to ONE_OTHER,
  "ia" to ONE_OTHER, "id" to OTHER_ONLY, "ie" to ONE_OTHER, "ig" to OTHER_ONLY, "ii" to OTHER_ONLY, "io" to ONE_OTHER,
  "is" to ONE_OTHER, "it" to ONE_MANY_OTHER, "iu" to ONE_TWO_OTHER, "ja" to OTHER_ONLY, "jbo" to OTHER_ONLY, "jgo" to ONE_OTHER,
  "jmc" to ONE_OTHER, "jv" to OTHER_ONLY, "ka" to ONE_OTHER, "kab" to ONE_OTHER, "kaj" to ONE_OTHER, "kam" to OTHER_ONLY,
  "kcg" to ONE_OTHER, "kde" to OTHER_ONLY, "kea" to OTHER_ONLY, "kgp" to OTHER_ONLY, "khq" to OTHER_ONLY, "ki" to OTHER_ONLY,
  "kk" to ONE_OTHER, "kkj" to ONE_OTHER, "kl" to ONE_OTHER, "kln" to OTHER_ONLY, "km" to OTHER_ONLY, "kn" to ONE_OTHER,
  "ko" to OTHER_ONLY, "kok" to ONE_OTHER, "ks" to ONE_OTHER, "ksb" to ONE_OTHER, "ksf" to OTHER_ONLY, "ksh" to ZERO_ONE_OTHER,
  "ku" to ONE_OTHER, "kw" to ALL_SIX, "kxv" to OTHER_ONLY, "ky" to ONE_OTHER, "lag" to ZERO_ONE_OTHER, "lb" to ONE_OTHER,
  "lg" to ONE_OTHER, "lij" to ONE_OTHER, "lkt" to OTHER_ONLY, "lld" to ONE_MANY_OTHER, "lmo" to OTHER_ONLY, "ln" to ONE_OTHER,
  "lo" to OTHER_ONLY, "lrc" to OTHER_ONLY, "lt" to ONE_FEW_MANY_OTHER, "lu" to OTHER_ONLY, "luo" to OTHER_ONLY,
  "luy" to OTHER_ONLY, "lv" to ZERO_ONE_OTHER, "mai" to OTHER_ONLY, "mas" to ONE_OTHER, "mer" to OTHER_ONLY, "mfe" to OTHER_ONLY,
  "mg" to ONE_OTHER, "mgh" to OTHER_ONLY, "mgo" to ONE_OTHER, "mi" to OTHER_ONLY, "mk" to ONE_OTHER, "ml" to ONE_OTHER,
  "mn" to ONE_OTHER, "mni" to OTHER_ONLY, "mr" to ONE_OTHER, "ms" to OTHER_ONLY, "mt" to ONE_TWO_FEW_MANY_OTHER,
  "mua" to OTHER_ONLY, "my" to OTHER_ONLY, "mzn" to OTHER_ONLY, "nah" to ONE_OTHER, "naq" to ONE_TWO_OTHER, "nb" to ONE_OTHER,
  "nd" to ONE_OTHER, "nds" to OTHER_ONLY, "ne" to ONE_OTHER, "nl" to ONE_OTHER, "nmg" to OTHER_ONLY, "nn" to ONE_OTHER,
  "nnh" to ONE_OTHER, "no" to ONE_OTHER, "nqo" to OTHER_ONLY, "nr" to ONE_OTHER, "nso" to ONE_OTHER, "nus" to OTHER_ONLY,
  "ny" to ONE_OTHER, "nyn" to ONE_OTHER, "oc" to OTHER_ONLY, "om" to ONE_OTHER, "or" to ONE_OTHER, "os" to ONE_OTHER,
  "osa" to OTHER_ONLY, "pa" to ONE_OTHER, "pap" to ONE_OTHER, "pcm" to ONE_OTHER, "pl" to ONE_FEW_MANY_OTHER,
  "pms" to OTHER_ONLY, "prg" to ZERO_ONE_OTHER, "ps" to ONE_OTHER, "pt" to ONE_MANY_OTHER, "qu" to OTHER_ONLY,
  "raj" to OTHER_ONLY, "rm" to ONE_OTHER, "rn" to OTHER_ONLY, "ro" to ONE_FEW_OTHER, "rof" to ONE_OTHER,
  "ru" to ONE_FEW_MANY_OTHER, "rw" to OTHER_ONLY, "rwk" to ONE_OTHER, "sa" to OTHER_ONLY, "sah" to OTHER_ONLY,
  "saq" to ONE_OTHER, "sat" to ONE_TWO_OTHER, "sbp" to OTHER_ONLY, "sc" to ONE_OTHER, "scn" to ONE_MANY_OTHER, "sd" to ONE_OTHER,
  "sdh" to ONE_OTHER, "se" to ONE_TWO_OTHER, "seh" to ONE_OTHER, "ses" to OTHER_ONLY, "sg" to OTHER_ONLY,
  "sgs" to ONE_TWO_FEW_MANY_OTHER, "shi" to ONE_FEW_OTHER, "shn" to OTHER_ONLY, "si" to ONE_OTHER, "sk" to ONE_FEW_MANY_OTHER,
  "sl" to ONE_TWO_FEW_OTHER, "sma" to ONE_TWO_OTHER, "smi" to ONE_TWO_OTHER, "smj" to ONE_TWO_OTHER, "smn" to ONE_TWO_OTHER,
  "sms" to ONE_TWO_OTHER, "sn" to ONE_OTHER, "so" to ONE_OTHER, "sq" to ONE_OTHER, "sr" to ONE_FEW_OTHER, "ss" to ONE_OTHER,
  "ssy" to ONE_OTHER, "st" to ONE_OTHER, "su" to OTHER_ONLY, "sv" to ONE_OTHER, "sw" to ONE_OTHER, "syr" to ONE_OTHER,
  "szl" to OTHER_ONLY, "ta" to ONE_OTHER, "te" to ONE_OTHER, "teo" to ONE_OTHER, "tg" to OTHER_ONLY, "th" to OTHER_ONLY,
  "ti" to ONE_OTHER, "tig" to ONE_OTHER, "tk" to ONE_OTHER, "tn" to ONE_OTHER, "to" to OTHER_ONLY, "tok" to OTHER_ONLY,
  "tpi" to OTHER_ONLY, "tr" to ONE_OTHER, "ts" to ONE_OTHER, "tt" to OTHER_ONLY, "twq" to OTHER_ONLY, "tyv" to OTHER_ONLY,
  "tzm" to ONE_OTHER, "ug" to ONE_OTHER, "uk" to ONE_FEW_MANY_OTHER, "ur" to ONE_OTHER, "uz" to ONE_OTHER, "vai" to OTHER_ONLY,
  "ve" to ONE_OTHER, "vec" to ONE_MANY_OTHER, "vi" to OTHER_ONLY, "vmw" to OTHER_ONLY, "vo" to ONE_OTHER, "vun" to ONE_OTHER,
  "wa" to ONE_OTHER, "wae" to ONE_OTHER, "wo" to OTHER_ONLY, "xh" to ONE_OTHER, "xnr" to OTHER_ONLY, "xog" to ONE_OTHER,
  "yav" to OTHER_ONLY, "yi" to ONE_OTHER, "yo" to OTHER_ONLY, "yrl" to OTHER_ONLY, "yue" to OTHER_ONLY, "za" to OTHER_ONLY,
  "zgh" to OTHER_ONLY, "zh" to OTHER_ONLY, "zu" to ONE_OTHER
)
