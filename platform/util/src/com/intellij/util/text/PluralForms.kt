// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import org.jetbrains.annotations.ApiStatus
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Supports ICU-style plural selection for message bundle patterns.
 *
 * [select] implements the complete integer part of the CLDR 48 cardinal plural rules (operands `v = 0`, `f = 0`, `e = 0`),
 * matching the data shipped in ICU4J 78.3, keyed by the language subtag of the locale
 * (plus the country subtag for `pt-PT`, the only region with its own cardinal rule).
 * Legacy language codes (`iw`, `in`, `ji`, `jw`, `tl`, `mo`, `sh`) are listed next to their modern spellings
 * because [Locale] may report either, depending on `java.locale.useOldISOCodes`.
 * Fractional values are not covered: callers format counts and durations, which are integral,
 * and must map fractions to `other`.
 *
 * [replaceArguments] resolves ICU-style `{N,plural,...}` arguments in a pattern
 * before [java.text.MessageFormat] construction, because [java.text.MessageFormat]
 * rejects the `plural` format type.
 */
@ApiStatus.Internal
object PluralForms {
  const val FORMAT_TYPE: String = "plural"

  /**
   * Returns the CLDR plural category (`zero`, `one`, `two`, `few`, `many` or `other`)
   * for the given integer [value] in the given [locale].
   * Unknown languages use the English rule (`one` for 1, `other` otherwise),
   * which is also the exact CLDR rule for the largest group of languages (en, de, nl, tr, sv, el, hu, ...).
   */
  @JvmStatic
  fun select(locale: Locale, value: Long): String {
    // abs(Long.MIN_VALUE) overflows, so reduce it the way the BigInteger overload does
    val n = if (value == Long.MIN_VALUE) 10_000_000L - value % 1_000_000L else abs(value)
    // European Portuguese does not treat 0 as `one` and follows the Iberian rule instead
    val language = if (locale.language == "pt" && locale.country == "PT") "es" else locale.language
    return when (language) {
      "agq", "ba", "bas", "bgc", "bm", "bo", "bua", "ccp", "dav", "dje", "dua", "dyo", "dz", "ebu", "ewo", "gaa",
      "guz", "id", "in", "ig", "ii", "ja", "jv", "jw", "kam", "kde", "kea", "kgp", "khq", "ki", "kln", "km", "ko",
      "ksf", "kxv", "lkt", "lmo", "lo", "lrc", "lu", "luo", "luy", "mai", "mer", "mfe", "mgh", "mi", "mni", "ms",
      "mua", "my", "mzn", "nds", "nmg", "nqo", "nus", "oc", "pms", "qu", "raj", "rn", "rw", "sa", "sah", "sbp",
      "ses", "sg", "shn", "su", "szl", "tg", "th", "to", "tok", "tt", "twq", "tyv", "vai", "vi", "vmw", "wo",
      "xnr", "yav", "yo", "yrl", "yue", "za", "zgh", "zh" -> "other"
      "ak", "am", "as", "bho", "bn", "csw", "doi", "fa", "ff", "gu", "guw", "hi", "hy", "kab", "kn", "kok",
      "ln", "mg", "nso", "pa", "pcm", "si", "ti", "wa", "zu" -> if (n <= 1L) "one" else "other"
      "fr", "pt" -> when {
        n <= 1L -> "one"
        n % 1_000_000L == 0L -> "many"
        else -> "other"
      }
      "es", "it", "ca", "scn", "vec", "lld" -> when {
        n == 1L -> "one"
        n != 0L && n % 1_000_000L == 0L -> "many"
        else -> "other"
      }
      "ru", "uk", "be" -> when {
        n % 10L == 1L && n % 100L != 11L -> "one"
        n % 10L in 2L..4L && n % 100L !in 12L..14L -> "few"
        else -> "many"
      }
      "sr", "hr", "bs", "sh" -> when {
        n % 10L == 1L && n % 100L != 11L -> "one"
        n % 10L in 2L..4L && n % 100L !in 12L..14L -> "few"
        else -> "other"
      }
      "pl" -> when {
        n == 1L -> "one"
        n % 10L in 2L..4L && n % 100L !in 12L..14L -> "few"
        else -> "many"
      }
      "lt" -> when {
        n % 100L in 11L..19L -> "other"
        n % 10L == 1L -> "one"
        n % 10L in 2L..9L -> "few"
        else -> "other"
      }
      "lv", "prg" -> when {
        n % 10L == 0L || n % 100L in 11L..19L -> "zero"
        n % 10L == 1L -> "one"
        else -> "other"
      }
      "cs", "sk" -> when (n) {
        1L -> "one"
        in 2L..4L -> "few"
        else -> "other"
      }
      "sl", "dsb", "hsb" -> when (n % 100L) {
        1L -> "one"
        2L -> "two"
        3L, 4L -> "few"
        else -> "other"
      }
      "ro", "mo" -> when {
        n == 1L -> "one"
        n == 0L || n % 100L in 1L..19L -> "few"
        else -> "other"
      }
      "he", "iw", "iu", "naq", "sat", "se", "sma", "smi", "smj", "smn", "sms" -> when (n) {
        1L -> "one"
        2L -> "two"
        else -> "other"
      }
      "is", "mk" -> if (n % 10L == 1L && n % 100L != 11L) "one" else "other"
      "fil", "ceb", "tl" -> if (n % 10L != 4L && n % 10L != 6L && n % 10L != 9L) "one" else "other"
      "tzm" -> if (n <= 1L || n in 11L..99L) "one" else "other"
      "shi" -> when {
        n <= 1L -> "one"
        n in 2L..10L -> "few"
        else -> "other"
      }
      "gd" -> when (n) {
        1L, 11L -> "one"
        2L, 12L -> "two"
        in 3L..10L, in 13L..19L -> "few"
        else -> "other"
      }
      "ga" -> when (n) {
        1L -> "one"
        2L -> "two"
        in 3L..6L -> "few"
        in 7L..10L -> "many"
        else -> "other"
      }
      "gv" -> when {
        n % 10L == 1L -> "one"
        n % 10L == 2L -> "two"
        n % 100L == 0L || n % 100L == 20L || n % 100L == 40L || n % 100L == 60L || n % 100L == 80L -> "few"
        else -> "other"
      }
      "br" -> when {
        n % 10L == 1L && n % 100L != 11L && n % 100L != 71L && n % 100L != 91L -> "one"
        n % 10L == 2L && n % 100L != 12L && n % 100L != 72L && n % 100L != 92L -> "two"
        (n % 10L == 3L || n % 10L == 4L || n % 10L == 9L) &&
        n % 100L !in 10L..19L && n % 100L !in 70L..79L && n % 100L !in 90L..99L -> "few"
        n != 0L && n % 1_000_000L == 0L -> "many"
        else -> "other"
      }
      "mt" -> when {
        n == 1L -> "one"
        n == 2L -> "two"
        n == 0L || n % 100L in 3L..10L -> "few"
        n % 100L in 11L..19L -> "many"
        else -> "other"
      }
      "ar", "ars" -> when {
        n == 0L -> "zero"
        n == 1L -> "one"
        n == 2L -> "two"
        n % 100L in 3L..10L -> "few"
        n % 100L in 11L..99L -> "many"
        else -> "other"
      }
      "cy" -> when (n) {
        0L -> "zero"
        1L -> "one"
        2L -> "two"
        3L -> "few"
        6L -> "many"
        else -> "other"
      }
      "kw" -> when {
        n == 0L -> "zero"
        n == 1L -> "one"
        n % 100L == 2L || n % 100L == 22L || n % 100L == 42L || n % 100L == 62L || n % 100L == 82L ||
        n % 1000L == 0L && (n % 100_000L in 1000L..20_000L || n % 100_000L == 40_000L ||
                            n % 100_000L == 60_000L || n % 100_000L == 80_000L) ||
        n % 1_000_000L == 100_000L -> "two"
        n % 100L == 3L || n % 100L == 23L || n % 100L == 43L || n % 100L == 63L || n % 100L == 83L -> "few"
        n % 100L == 1L || n % 100L == 21L || n % 100L == 41L || n % 100L == 61L || n % 100L == 81L -> "many"
        else -> "other"
      }
      "blo", "cv", "ksh", "lag" -> when (n) {
        0L -> "zero"
        1L -> "one"
        else -> "other"
      }
      else -> if (n == 1L) "one" else "other"
    }
  }

  /**
   * [select] for values that may not fit in a [Long]: the CLDR integer rules only compare the value
   * against small constants and remainders of divisors of 1,000,000, so an oversized value is reduced
   * to a congruent value modulo 1,000,000 that stays above every compared constant.
   */
  @JvmStatic
  fun select(locale: Locale, value: BigInteger): String {
    val reduced = if (value.bitLength() <= 63) value.toLong() else value.abs().mod(ONE_MILLION).toLong() + 10_000_000L
    return select(locale, reduced)
  }

  private val ONE_MILLION = BigInteger.valueOf(1_000_000L)

  /**
   * Replaces every top-level `{N,plural,...}` argument in [pattern] with its selected branch.
   * A branch is selected by exact `=N` match on the parameter value first, then by the CLDR category
   * from [select] with `offset` applied, then by the mandatory `other` branch.
   * `=N` and `offset:` accept every ICU numeric form: a sign, a fraction, and an exponent.
   * Unquoted `#` in the selected branch becomes the (offset-adjusted) formatted number.
   * Malformed plural arguments and non-Number parameters produce an [IllegalArgumentException],
   * matching the error path of an invalid [java.text.MessageFormat] pattern.
   */
  fun replaceArguments(pattern: String, locale: Locale, params: Array<out Any?>): String {
    var result = pattern
    while (true) {
      result = replaceFirstArgument(result, locale, params) ?: return result
    }
  }

  private fun replaceFirstArgument(pattern: String, locale: Locale, params: Array<out Any?>): String? {
    var inQuotes = false
    var depth = 0
    var i = 0
    while (i < pattern.length) {
      when (pattern[i]) {
        '\'' -> if (i + 1 < pattern.length && pattern[i + 1] == '\'') i++ else inQuotes = !inQuotes
        '{' -> if (!inQuotes) {
          if (depth == 0) {
            val replaced = tryReplaceArgumentAt(pattern, i, locale, params)
            if (replaced != null) return replaced
          }
          depth++
        }
        // MessageFormat treats an unmatched top-level '}' as literal text
        '}' -> if (!inQuotes && depth > 0) depth--
      }
      i++
    }
    return null
  }

  private fun tryReplaceArgumentAt(pattern: String, blockStart: Int, locale: Locale, params: Array<out Any?>): String? {
    val indexEnd = pattern.indexOf(',', blockStart + 1)
    if (indexEnd < 0) return null
    val argIndexText = pattern.substring(blockStart + 1, indexEnd).trim()
    if (argIndexText.isEmpty() || !argIndexText.all { it in '0'..'9' }) return null
    val typeEnd = pattern.indexOf(',', indexEnd + 1)
    if (typeEnd < 0 || pattern.substring(indexEnd + 1, typeEnd).trim() != FORMAT_TYPE) return null

    var pos = skipWhitespace(pattern, typeEnd + 1)
    var offset = BigDecimal.ZERO
    if (pattern.startsWith("offset:", pos)) {
      pos = skipWhitespace(pattern, pos + "offset:".length)
      val numberStart = pos
      while (pos < pattern.length && (pattern[pos] in '0'..'9' || pattern[pos] in "+-.eE")) pos++
      offset = requireNotNull(pattern.substring(numberStart, pos).toBigDecimalOrNull()) { "Malformed plural offset: $pattern" }
    }

    val branches = HashMap<String, String>()
    val explicitBranches = ArrayList<Pair<BigDecimal, String>>()
    var blockEnd: Int
    while (true) {
      pos = skipWhitespace(pattern, pos)
      require(pos < pattern.length) { "Unterminated plural argument: $pattern" }
      if (pattern[pos] == '}') {
        blockEnd = pos + 1
        break
      }
      val selectorStart = pos
      while (pos < pattern.length && pattern[pos] != '{' && pattern[pos] != '}' && !pattern[pos].isWhitespace()) pos++
      val selector = pattern.substring(selectorStart, pos)
      // ICU accepts any numeric value after '=', including a sign, a fraction, and an exponent
      val explicitValue = if (selector.startsWith("=")) {
        requireNotNull(selector.substring(1).toBigDecimalOrNull()) { "Invalid plural selector '$selector': $pattern" }
      }
      else {
        require(selector in CATEGORIES) { "Invalid plural selector '$selector': $pattern" }
        null
      }
      pos = skipWhitespace(pattern, pos)
      require(pos < pattern.length && pattern[pos] == '{') { "Missing '{' after plural selector '$selector': $pattern" }
      val branchStart = ++pos
      var branchDepth = 1
      var inQuotes = false
      while (pos < pattern.length && branchDepth > 0) {
        when (pattern[pos]) {
          '\'' -> if (pos + 1 < pattern.length && pattern[pos + 1] == '\'') pos++ else inQuotes = !inQuotes
          '{' -> if (!inQuotes) branchDepth++
          '}' -> if (!inQuotes) branchDepth--
        }
        pos++
      }
      require(branchDepth == 0) { "Unterminated plural branch '$selector': $pattern" }
      val branchText = pattern.substring(branchStart, pos - 1)
      when {
        explicitValue == null -> branches.putIfAbsent(selector, branchText)
        explicitBranches.none { it.first.compareTo(explicitValue) == 0 } -> explicitBranches.add(explicitValue to branchText)
      }
    }
    val otherBranch = requireNotNull(branches["other"]) { "Plural argument must have an 'other' branch: $pattern" }

    val argIndex = argIndexText.toInt()
    val number = requireNotNull(params.getOrNull(argIndex) as? Number) {
      "Plural argument {$argIndex} needs a Number parameter, got: ${params.getOrNull(argIndex)}"
    }
    val integral: BigInteger? = when (number) {
      is BigInteger -> number
      is BigDecimal -> number.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toBigInteger()
      is Long, is Int, is Short, is Byte -> number.toLong().toBigInteger()
      is Double, is Float -> number.toDouble().takeIf { it.isFinite() && floor(it) == it }?.toBigDecimal()?.toBigInteger()
      // integral wrappers like AtomicLong must not round through Double; toDouble() only screens out fractions
      else -> number.toDouble().takeIf { it.isFinite() && floor(it) == it }?.let { number.toLong().toBigInteger() }
    }
    val exactValue: BigDecimal? = when {
      integral != null -> integral.toBigDecimal()
      number is BigDecimal -> number
      else -> number.toDouble().takeIf { it.isFinite() }?.toBigDecimal()
    }
    // a Double parameter carries double precision, so an explicit selector may also match its double value
    val floating = if (number is Double || number is Float) number.toDouble() else null
    val explicitBranch = explicitBranches.firstOrNull { (selectorValue, _) ->
      (exactValue != null && selectorValue.compareTo(exactValue) == 0) || (floating != null && selectorValue.toDouble() == floating)
    }?.second
    val adjusted = exactValue?.subtract(offset)
    val adjustedIntegral = adjusted?.stripTrailingZeros()?.takeIf { it.scale() <= 0 }?.toBigIntegerExact()
    val branch = explicitBranch ?: adjustedIntegral?.let { branches[select(locale, it)] } ?: otherBranch
    val numberText = when {
      offset.signum() == 0 -> "{$argIndex,number}"
      adjustedIntegral != null -> NumberFormat.getIntegerInstance(locale).format(adjustedIntegral).replace("'", "''")
      adjusted != null -> NumberFormat.getInstance(locale).format(adjusted).replace("'", "''")
      else -> NumberFormat.getInstance(locale).format(number.toDouble() - offset.toDouble()).replace("'", "''")
    }
    return pattern.substring(0, blockStart) + replaceNumberSigns(branch, numberText) + pattern.substring(blockEnd)
  }

  private fun replaceNumberSigns(branch: String, numberText: String): String {
    if (!branch.contains('#')) return branch
    val sb = StringBuilder(branch.length + numberText.length)
    var inQuotes = false
    var depth = 0
    var i = 0
    while (i < branch.length) {
      val c = branch[i]
      when {
        c == '\'' -> {
          if (i + 1 < branch.length && branch[i + 1] == '\'') {
            sb.append("''")
            i++
          }
          else {
            inQuotes = !inQuotes
            sb.append(c)
          }
        }
        inQuotes -> sb.append(c)
        c == '#' && depth == 0 -> sb.append(numberText)
        else -> {
          if (c == '{') depth++
          if (c == '}') depth--
          sb.append(c)
        }
      }
      i++
    }
    return sb.toString()
  }

  private fun skipWhitespace(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i].isWhitespace()) i++
    return i
  }

  private val CATEGORIES = setOf("zero", "one", "two", "few", "many", "other")
}
