// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import com.intellij.AbstractBundle
import com.intellij.BundleBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.ListResourceBundle
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.atomic.AtomicLong

class BundleBasePluralPatternTest {
  private val englishBundle: ResourceBundle
    get() = ResourceBundle.getBundle(TestBundle::class.java.name, Locale.ENGLISH)

  private val russianBundle: ResourceBundle
    get() = ResourceBundle.getBundle(TestBundle::class.java.name, Locale.forLanguageTag("ru"))

  @Test
  fun categorySelection() {
    assertThat(BundleBase.message(englishBundle, "days", 1)).isEqualTo("1 day")
    assertThat(BundleBase.message(englishBundle, "days", 0)).isEqualTo("0 days")
    assertThat(BundleBase.message(englishBundle, "days", 5)).isEqualTo("5 days")
  }

  @Test
  fun numberSignFormatsWithGrouping() {
    assertThat(BundleBase.message(englishBundle, "days", 1234)).isEqualTo("1,234 days")
  }

  @Test
  fun quotedBracesAndDoubledApostrophes() {
    assertThat(BundleBase.message(englishBundle, "quoted", 1)).isEqualTo("{1 item}")
    assertThat(BundleBase.message(englishBundle, "quoted", 2)).isEqualTo("{2 items}")
    assertThat(BundleBase.message(englishBundle, "apostrophe", 1)).isEqualTo("1 day's worth")
  }

  @Test
  fun quotedPluralBlockStaysLiteral() {
    assertThat(BundleBase.message(englishBundle, "literal.plural", 1)).isEqualTo("{0,plural,one{a}other{b}} 1")
  }

  @Test
  fun exactValueSelector() {
    assertThat(BundleBase.message(englishBundle, "exact.zero", 0)).isEqualTo("no days")
    assertThat(BundleBase.message(englishBundle, "exact.zero", 1)).isEqualTo("1 day")
    assertThat(BundleBase.message(englishBundle, "exact.zero", 5)).isEqualTo("5 days")
  }

  @Test
  fun offsetAppliesToCategoryAndNumberSignButNotToExactMatch() {
    assertThat(BundleBase.message(englishBundle, "offset", 0)).isEqualTo("nobody")
    assertThat(BundleBase.message(englishBundle, "offset", 1)).isEqualTo("just you")
    assertThat(BundleBase.message(englishBundle, "offset", 2)).isEqualTo("you and 1 other")
    assertThat(BundleBase.message(englishBundle, "offset", 5)).isEqualTo("you and 4 others")
  }

  @Test
  fun twoPluralArgumentsInOneMessage() {
    assertThat(BundleBase.message(englishBundle, "two.plurals", 1, 2)).isEqualTo("1 day and 2 weeks")
    assertThat(BundleBase.message(englishBundle, "two.plurals", 2, 1)).isEqualTo("2 days and 1 week")
  }

  @Test
  fun nestedArgumentInsideBranch() {
    assertThat(BundleBase.message(englishBundle, "nested.param", 1, "src")).isEqualTo("1 file in src")
    assertThat(BundleBase.message(englishBundle, "nested.param", 2, "src")).isEqualTo("2 files in src")
  }

  @Test
  fun whitespaceAroundSelectorsIsTolerated() {
    assertThat(BundleBase.message(englishBundle, "spaced", 1)).isEqualTo("1 day")
    assertThat(BundleBase.message(englishBundle, "spaced", 2)).isEqualTo("2 days")
  }

  @Test
  fun missingOtherBranchFallsBackToInvalidFormatMarker() {
    assertThat(BundleBase.message(englishBundle, "missing.other", 1)).startsWith("!invalid format:")
  }

  @Test
  fun nonNumberParameterFallsBackToInvalidFormatMarker() {
    assertThat(BundleBase.message(englishBundle, "days", "x")).startsWith("!invalid format:")
  }

  @Test
  fun fractionalValueSelectsOtherBranch() {
    assertThat(BundleBase.message(englishBundle, "days", 1.5)).isEqualTo("1.5 days")
    assertThat(BundleBase.message(englishBundle, "days", BigDecimal("1.5"))).isEqualTo("1.5 days")
  }

  @Test
  fun integralDoubleSelectsByValue() {
    assertThat(BundleBase.message(englishBundle, "days", 1.0)).isEqualTo("1 day")
    assertThat(BundleBase.message(englishBundle, "offset", 2.0)).isEqualTo("you and 1 other")
  }

  @Test
  fun unmatchedTopLevelClosingBraceStaysLiteral() {
    assertThat(BundleBase.message(englishBundle, "stray.brace", 1)).isEqualTo("} 1 day")
  }

  @Test
  fun bigIntegerBeyondLongRangeIsNotTruncated() {
    val big = BigInteger.TWO.pow(64) + BigInteger.ONE // truncates to 1 as a Long
    assertThat(BundleBase.message(englishBundle, "days", big)).isEqualTo("18,446,744,073,709,551,617 days")
    assertThat(BundleBase.message(englishBundle, "offset", big)).isEqualTo("you and 18,446,744,073,709,551,616 others")
  }

  @Test
  fun bigDecimalBeyondLongRangeIsNotTruncated() {
    val big = BigDecimal("18446744073709551617") // truncates to 1 as a Long
    assertThat(BundleBase.message(englishBundle, "days", big)).isEqualTo("18,446,744,073,709,551,617 days")
    assertThat(BundleBase.message(englishBundle, "offset", big)).isEqualTo("you and 18,446,744,073,709,551,616 others")
  }

  @Test
  fun atomicLongIsNotRoundedThroughDouble() {
    assertThat(BundleBase.message(englishBundle, "exact.max", AtomicLong(Long.MAX_VALUE))).isEqualTo("max")
    // 2^54 + 37 rounds to ...020 as a Double, which would flip the Russian category from `one` to `many`
    assertThat(BundleBase.message(russianBundle, "category.ru", AtomicLong(18_014_398_509_482_021))).isEqualTo("one")
  }

  @Test
  fun rootBundleUsesEnglishPluralRulesRegardlessOfDefaultLocale() {
    val rootBundle = ResourceBundle.getBundle(TestBundle::class.java.name, Locale.ROOT)
    assertThat(rootBundle.locale).isEqualTo(Locale.ROOT)
    withDefaultLocale(Locale.forLanguageTag("ru")) {
      // the Russian integer rule selects `one` for 21; the English base content must not use it
      assertThat(BundleBase.message(rootBundle, "days", 21)).isEqualTo("21 days")
      assertThat(BundleBase.message(rootBundle, "days", 1)).isEqualTo("1 day")
    }
  }

  private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
      block()
    }
    finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun icuNumericSelectors() {
    assertThat(BundleBase.message(englishBundle, "exact.negative", -1)).isEqualTo("minus one")
    assertThat(BundleBase.message(englishBundle, "exact.negative", 1)).isEqualTo("1 day")
    assertThat(BundleBase.message(englishBundle, "exact.fraction", 1.5)).isEqualTo("one and a half")
    assertThat(BundleBase.message(englishBundle, "exact.fraction", BigDecimal("1.50"))).isEqualTo("one and a half")
    assertThat(BundleBase.message(englishBundle, "exact.fraction", 2.5)).isEqualTo("2.5 days")
    // 0.1 has no exact binary form; the selector must match the double value
    assertThat(BundleBase.message(englishBundle, "exact.tenth", 0.1)).isEqualTo("exactly a tenth")
    assertThat(BundleBase.message(englishBundle, "exact.padded", 1)).isEqualTo("padded one")
  }

  @Test
  fun icuNumericOffsets() {
    assertThat(BundleBase.message(englishBundle, "offset.negative", 0)).isEqualTo("1 item")
    assertThat(BundleBase.message(englishBundle, "offset.negative", 1)).isEqualTo("2 items")
    assertThat(BundleBase.message(englishBundle, "offset.fraction", 1.5)).isEqualTo("1 thing")
    assertThat(BundleBase.message(englishBundle, "offset.fraction", 2)).isEqualTo("1.5 things")
  }

  @Test
  fun exactSelectorDoesNotMatchThroughDoubleRounding() {
    // 9223372036854775806 and Long.MAX_VALUE collapse to the same double
    assertThat(BundleBase.message(englishBundle, "exact.near.max", AtomicLong(Long.MAX_VALUE))).isEqualTo("other")
  }

  @Test
  fun malformedNumericSyntaxFallsBackToInvalidFormatMarker() {
    assertThat(BundleBase.message(englishBundle, "bad.selector", 1)).startsWith("!invalid format:")
    assertThat(BundleBase.message(englishBundle, "bad.offset", 1)).startsWith("!invalid format:")
  }

  @Test
  fun inheritedMessageUsesThePluralRulesOfTheDefiningBundle() {
    val russian = Locale.forLanguageTag("ru")
    val bundle = ResourceBundle.getBundle("com.intellij.util.text.InheritedPluralBundle", russian, javaClass.classLoader, AbstractBundle.getControl())
    assertThat(bundle.locale).isEqualTo(russian)
    // `days` lives only in the English base file; the Russian rule selects `one` for 21, but the English rule must win
    assertThat(BundleBase.message(bundle, "days", 21)).isEqualTo("21 days")
    assertThat(BundleBase.message(bundle, "days", 1)).isEqualTo("1 day")
    // `days.ru` lives in the Russian file and keeps the Russian rules
    assertThat(BundleBase.message(bundle, "days.ru", 21)).isEqualTo("21 день")
    assertThat(BundleBase.message(bundle, "days.ru", 5)).isEqualTo("5 дней")
  }

  @Test
  fun russianBundleUsesRussianCategories() {
    assertThat(BundleBase.message(russianBundle, "days.ru", 1)).isEqualTo("1 день")
    assertThat(BundleBase.message(russianBundle, "days.ru", 2)).isEqualTo("2 дня")
    assertThat(BundleBase.message(russianBundle, "days.ru", 5)).isEqualTo("5 дней")
    assertThat(BundleBase.message(russianBundle, "days.ru", 21)).isEqualTo("21 день")
    assertThat(BundleBase.message(russianBundle, "days.ru", 111)).isEqualTo("111 дней")
  }

  open class TestBundle : ListResourceBundle() {
    override fun getContents(): Array<Array<Any>> = arrayOf(
      arrayOf("days", "{0,plural,one{# day}other{# days}}"),
      arrayOf("quoted", "{0,plural,one{'{'# item'}'}other{'{'# items'}'}}"),
      arrayOf("apostrophe", "{0,plural,one{# day''s worth}other{# days'' worth}}"),
      arrayOf("literal.plural", "'{0,plural,one{a}other{b}}' {0}"),
      arrayOf("exact.zero", "{0,plural,=0{no days}one{# day}other{# days}}"),
      arrayOf("offset", "{0,plural,offset:1 =0{nobody}=1{just you}one{you and # other}other{you and # others}}"),
      arrayOf("two.plurals", "{0,plural,one{# day}other{# days}} and {1,plural,one{# week}other{# weeks}}"),
      arrayOf("nested.param", "{0,plural,one{# file in {1}}other{# files in {1}}}"),
      arrayOf("spaced", "{0, plural, one {# day} other {# days}}"),
      arrayOf("missing.other", "{0,plural,one{# day}}"),
      arrayOf("stray.brace", "} {0,plural,one{# day}other{# days}}"),
      arrayOf("exact.max", "{0,plural,=9223372036854775807{max}other{other}}"),
      arrayOf("exact.near.max", "{0,plural,=9223372036854775806{near max}other{other}}"),
      arrayOf("exact.negative", "{0,plural,=-1{minus one}one{# day}other{# days}}"),
      arrayOf("exact.fraction", "{0,plural,=1.5{one and a half}other{# days}}"),
      arrayOf("exact.tenth", "{0,plural,=0.1{exactly a tenth}other{# days}}"),
      arrayOf("exact.padded", "{0,plural,=01{padded one}other{# days}}"),
      arrayOf("offset.negative", "{0,plural,offset:-1 one{# item}other{# items}}"),
      arrayOf("offset.fraction", "{0,plural,offset:0.5 one{# thing}other{# things}}"),
      arrayOf("bad.selector", "{0,plural,=1d{x}other{y}}"),
      arrayOf("bad.offset", "{0,plural,offset:x one{a}other{b}}"),
    )
  }

  @Suppress("unused", "ClassName")
  class TestBundle_en : TestBundle()

  @Suppress("unused", "ClassName")
  class TestBundle_ru : ListResourceBundle() {
    @Suppress("NonAsciiCharacters")
    override fun getContents(): Array<Array<Any>> = arrayOf(
      arrayOf("days.ru", "{0,plural,one{# день}few{# дня}many{# дней}other{# дня}}"),
      arrayOf("category.ru", "{0,plural,one{one}few{few}many{many}other{other}}"),
    )
  }
}
