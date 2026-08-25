// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.Locale

class PluralFormsTest {
  private fun select(languageTag: String, vararg values: Long): List<String> {
    val locale = Locale.forLanguageTag(languageTag)
    return values.map { PluralForms.select(locale, it) }
  }

  @Test
  fun english() {
    assertThat(select("en", 0, 1, 2, 5, 100)).containsExactly("other", "one", "other", "other", "other")
  }

  @Test
  fun russian() {
    assertThat(select("ru", 1, 2, 5, 11, 21, 103, 111))
      .containsExactly("one", "few", "many", "many", "one", "few", "many")
    assertThat(select("ru", 0, 12, 22)).containsExactly("many", "many", "few")
  }

  @Test
  fun ukrainianAndBelarusianFollowRussianRule() {
    assertThat(select("uk", 1, 3, 5)).containsExactly("one", "few", "many")
    assertThat(select("be", 1, 3, 5)).containsExactly("one", "few", "many")
  }

  @Test
  fun arabic() {
    assertThat(select("ar", 0, 1, 2, 3, 11, 100))
      .containsExactly("zero", "one", "two", "few", "many", "other")
  }

  @Test
  fun french() {
    assertThat(select("fr", 0, 1, 2)).containsExactly("one", "one", "other")
    assertThat(select("fr", 1_000_000)).containsExactly("many")
  }

  @Test
  fun portugueseTreatsZeroAsOne() {
    assertThat(select("pt", 0, 1, 2)).containsExactly("one", "one", "other")
  }

  @Test
  fun europeanPortugueseDoesNotTreatZeroAsOne() {
    assertThat(select("pt-PT", 0, 1, 2)).containsExactly("other", "one", "other")
    assertThat(select("pt-PT", 1_000_000)).containsExactly("many")
    assertThat(select("pt-BR", 0)).containsExactly("one")
  }

  @Test
  fun polish() {
    assertThat(select("pl", 0, 1, 2, 5, 12, 22)).containsExactly("many", "one", "few", "many", "many", "few")
  }

  @Test
  fun czechAndSlovak() {
    assertThat(select("cs", 0, 1, 2, 5)).containsExactly("other", "one", "few", "other")
    assertThat(select("sk", 1, 3, 5)).containsExactly("one", "few", "other")
  }

  @Test
  fun serbianHasNoManyCategory() {
    assertThat(select("sr", 1, 2, 5, 11, 21)).containsExactly("one", "few", "other", "other", "one")
  }

  @Test
  fun slovenian() {
    assertThat(select("sl", 1, 2, 3, 4, 5, 101, 102, 105))
      .containsExactly("one", "two", "few", "few", "other", "one", "two", "other")
  }

  @Test
  fun romanian() {
    assertThat(select("ro", 0, 1, 2, 19, 20, 101, 119, 120))
      .containsExactly("few", "one", "few", "few", "other", "few", "few", "other")
  }

  @Test
  fun icelandic() {
    assertThat(select("is", 1, 2, 11, 21, 111)).containsExactly("one", "other", "other", "one", "other")
  }

  @Test
  fun latvianHasZeroCategory() {
    assertThat(select("lv", 0, 1, 2, 10, 11, 21)).containsExactly("zero", "one", "other", "zero", "zero", "one")
  }

  @Test
  fun hebrewWithBothLanguageCodes() {
    assertThat(select("he", 1, 2, 3)).containsExactly("one", "two", "other")
    assertThat(select("iw", 2)).containsExactly("two")
  }

  @Test
  fun hindiTreatsZeroAsOne() {
    assertThat(select("hi", 0, 1, 2)).containsExactly("one", "one", "other")
  }

  @Test
  fun otherOnlyLanguages() {
    assertThat(select("ja", 0, 1, 2)).containsExactly("other", "other", "other")
    assertThat(select("zh", 1)).containsExactly("other")
    assertThat(select("ko", 1)).containsExactly("other")
    assertThat(select("th", 1)).containsExactly("other")
    assertThat(select("vi", 0, 1, 2)).containsExactly("other", "other", "other")
    assertThat(select("id", 1)).containsExactly("other")
  }

  @Test
  fun unknownLanguageFallsBackToEnglishRule() {
    assertThat(select("xx", 1, 2)).containsExactly("one", "other")
  }

  @Test
  fun negativeValuesUseAbsoluteValue() {
    assertThat(select("en", -1, -2)).containsExactly("one", "other")
    assertThat(select("ru", -21)).containsExactly("one")
  }

  @Test
  fun longMinValueUsesAbsoluteValue() {
    // abs(Long.MIN_VALUE) overflows; the absolute value ends in ...808, so n % 100 == 8 selects `few` in Arabic
    assertThat(select("ar", Long.MIN_VALUE)).containsExactly("few")
    assertThat(select("en", Long.MIN_VALUE)).containsExactly("other")
    assertThat(PluralForms.select(Locale.forLanguageTag("ar"), BigInteger.valueOf(Long.MIN_VALUE))).isEqualTo("few")
  }

  @Test
  fun bigIntegerWithinLongRangeSelectsExactly() {
    assertThat(PluralForms.select(Locale.ENGLISH, BigInteger.ONE)).isEqualTo("one")
    assertThat(PluralForms.select(Locale.ENGLISH, BigInteger.valueOf(-1))).isEqualTo("one")
    assertThat(PluralForms.select(Locale.ENGLISH, BigInteger.valueOf(Long.MAX_VALUE))).isEqualTo("other")
  }

  @Test
  fun bigIntegerBeyondLongRangeIsNotTruncated() {
    val twoPow64Plus1 = BigInteger.TWO.pow(64) + BigInteger.ONE // truncates to 1 as a Long
    assertThat(PluralForms.select(Locale.ENGLISH, twoPow64Plus1)).isEqualTo("other")
    assertThat(PluralForms.select(Locale.forLanguageTag("ru"), BigInteger("10000000000000000000021"))).isEqualTo("one")
    assertThat(PluralForms.select(Locale.forLanguageTag("ru"), BigInteger("10000000000000000000021").negate())).isEqualTo("one")
    assertThat(PluralForms.select(Locale.FRENCH, BigInteger.TEN.pow(30))).isEqualTo("many")
  }

  @Test
  fun matchesCldr48IntegerRules() {
    for ((languages, signature) in cldr48Groups) {
      for (tag in languages.split(',')) {
        assertSignature(Locale.forLanguageTag(tag), signature)
      }
    }
    assertSignature(Locale.forLanguageTag("pt-PT"), CLDR48_PT_PT_SIGNATURE)
  }

  private fun assertSignature(locale: Locale, signature: String) {
    val actual = buildString {
      for (value in conformanceBattery) {
        append(categoryLetter(PluralForms.select(locale, value)))
      }
    }
    assertThat(actual).describedAs(locale.toLanguageTag()).isEqualTo(signature)
  }

  private fun categoryLetter(category: String): Char = when (category) {
    "zero" -> 'z'
    "one" -> 'o'
    "two" -> 't'
    "few" -> 'f'
    "many" -> 'm'
    "other" -> 'x'
    else -> error(category)
  }

  private val conformanceBattery: LongArray = LongArray(131) { it.toLong() } + longArrayOf(
    200, 201, 202, 211, 222, 231, 300, 302, 312, 342, 401, 422, 500, 600, 700, 800, 900, 999,
    1000, 1001, 1002, 1011, 1021, 1121, 2000, 2001, 2011, 3000, 4000, 5000,
    10000, 20000, 21000, 40000, 41000, 60000, 80000, 99999,
    100000, 100001, 100002, 200000, 300000, 500000, 900000, 999999,
    1000000, 1000001, 1000002, 1100000, 2000000, 2000001,
    10000000, 100000000, 1000000000)

  // Generated from ICU4J 78.3 (CLDR 48): for every language, PluralRules.select over `conformanceBattery`,
  // one letter per value: z=zero, o=one, t=two, f=few, m=many, x=other. Languages sharing a signature are
  // grouped; legacy codes (iw, in, ji, jw, tl, mo, sh) are included next to their modern spellings.
  private val cldr48Groups: Map<String, String> = mapOf(
    "mo,ro" to
      "foffffffffffffffffffxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxfffffffffffffffffffxxxxxxxxxxxxfffxxxffxfxxxxxxxxfffxxxffxxxxxxxxxxxxffxxxxxxffxxfxxx",
    "mt" to
      "fotffffffffmmmmmmmmmxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxffffffffmmmmmmmmmxxxxxxxxxxxxxxmxxxxmxxxxxxxxxxxxmxxxxmxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "gv" to
      "fotxxxxxxxxotxxxxxxxfotxxxxxxxxotxxxxxxxfotxxxxxxxxotxxxxxxxfotxxxxxxxxotxxxxxxxfotxxxxxxxxot" +
      "xxxxxxxfotxxxxxxxxotxxxxxxxfotxxxxxxxxfototoftttotfffffxfotooofooffffffffffxfotffffxfotffofff",
    "pl" to
      "mofffmmmmmmmmmmmmmmmmmfffmmmmmmmfffmmmmmmmfffmmmmmmmfffmmmmmmmfffmmmmmmmfffmmmmmmmfffmmmmmmmf" +
      "ffmmmmmmmfffmmmmmmmmmmmmmmmmmfffmmmmmmmmfmfmmfmfmfmmmmmmmmfmmmmmmmmmmmmmmmmmmmfmmmmmmmfmmmmmm",
    "be,ru,uk" to
      "mofffmmmmmmmmmmmmmmmmofffmmmmmmofffmmmmmmofffmmmmmmofffmmmmmmofffmmmmmmofffmmmmmmofffmmmmmmof" +
      "ffmmmmmmofffmmmmmmmmmmmmmmmmofffmmmmmmmofmfomfmfofmmmmmmmofmoomommmmmmmmmmmmmofmmmmmmofmmommm",
    "shi" to
      "oofffffffffxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "ceb,fil,tl" to
      "ooooxoxooxooooxoxooxooooxoxooxooooxoxooxooooxoxooxooooxoxooxooooxoxooxooooxoxooxooooxoxooxooo" +
      "oxoxooxooooxoxooxooooxoxooxooooxoxooxooooooooooooooooooxoooooooooooooooooooxoooooooxooooooooo",
    "tzm" to
      "ooxxxxxxxxxoooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo" +
      "oooooooxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "fr,pt" to
      "ooxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxmxxxmxmmm",
    "ak,am,as,bho,bn,csw,doi,fa,ff,gu,guw,hi,hy,kab,kn,kok,ln,mg,nso,pa,pcm,si,ti,wa,zu" to
      "ooxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "lt" to
      "xoffffffffxxxxxxxxxxxoffffffffxoffffffffxoffffffffxoffffffffxoffffffffxoffffffffxoffffffffxof" +
      "fffffffxoffffffffxxxxxxxxxxxoffffffffxxofxfoxfxfofxxxxxfxofxooxoxxxxxxxxxxxfxofxxxxfxofxxoxxx",
    "bs,hr,sh,sr" to
      "xofffxxxxxxxxxxxxxxxxofffxxxxxxofffxxxxxxofffxxxxxxofffxxxxxxofffxxxxxxofffxxxxxxofffxxxxxxof" +
      "ffxxxxxxofffxxxxxxxxxxxxxxxxofffxxxxxxxofxfoxfxfofxxxxxxxofxooxoxxxxxxxxxxxxxofxxxxxxofxxoxxx",
    "cs,sk" to
      "xofffxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "gd" to
      "xotffffffffotfffffffxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "ga" to
      "xotffffmmmmxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "br" to
      "xotffxxxxfxxxxxxxxxxxotffxxxxfxotffxxxxfxotffxxxxfxotffxxxxfxotffxxxxfxxxxxxxxxxxotffxxxxfxxx" +
      "xxxxxxxxotffxxxxfxxxxxxxxxxxotffxxxxfxxotxtoxtxtotxxxxxxxotxooxoxxxxxxxxxxxxxotxxxxxmotxmommm",
    "dsb,hsb,sl" to
      "xotffxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxotffxxxxxxxxxxxxxxxxxxxxxxxxxxxotxxxxtxxoxxxxxxxxotxxxxoxxxxxxxxxxxxxotxxxxxxotxxoxxx",
    "he,iu,iw,naq,sat,se,sma,smi,smj,smn,sms" to
      "xotxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "is,mk" to
      "xoxxxxxxxxxxxxxxxxxxxoxxxxxxxxxoxxxxxxxxxoxxxxxxxxxoxxxxxxxxxoxxxxxxxxxoxxxxxxxxxoxxxxxxxxxox" +
      "xxxxxxxxoxxxxxxxxxxxxxxxxxxxoxxxxxxxxxxoxxxoxxxxoxxxxxxxxoxxooxoxxxxxxxxxxxxxoxxxxxxxoxxxoxxx",
    "ca,es,it,lld,scn,vec" to
      "xoxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxmxxxmxmmm",
    "af,an,asa,ast,az,bem,bez,bg,brx,ce,cgg,chr,ckb,da,de,dv,ee,el,en,eo,et,eu,fi,fo,fur,fy,gl,gsw,ha,haw,hu,ia,ie,io,jgo,ji,jmc,ka,kaj,kcg,kk,kkj,kl,ks,ksb,ku,ky,lb,lg,lij,mas,mgo,ml,mn,mr,nah,nb,nd,ne,nl,nn,nnh,no,nr,ny,nyn,om,or,os,pap,ps,rm,rof,rwk,saq,sc,sd,sdh,seh,sn,so,sq,ss,ssy,st,sv,sw,syr,ta,te,teo,tig,tk,tn,tr,ts,ug,ur,uz,ve,vo,vun,wae,xh,xog,yi" to
      "xoxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "agq,ba,bas,bgc,bm,bo,bua,ccp,dav,dje,dua,dyo,dz,ebu,ewo,gaa,guz,id,ig,ii,in,ja,jv,jw,kam,kde,kea,kgp,khq,ki,kln,km,ko,ksf,kxv,lkt,lmo,lo,lrc,lu,luo,luy,mai,mer,mfe,mgh,mi,mni,ms,mua,my,mzn,nds,nmg,nqo,nus,oc,pms,qu,raj,rn,rw,sa,sah,sbp,ses,sg,shn,su,szl,tg,th,to,tok,tt,twq,tyv,vai,vi,vmw,wo,xnr,yav,yo,yrl,yue,za,zgh,zh" to
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "ar,ars" to
      "zotffffffffmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm" +
      "mmmmmmmxxxffffffffmmmmmmmmmmmmmmmmmmmmxxxmmmxxmmxmxxxxxmxxxmmmxxmxxxxxxxxxxmxxxxxxxmxxxxxxxxx",
    "cy" to
      "zotfxxmxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "kw" to
      "zotfxxxxxxxxxxxxxxxxxmtfxxxxxxxxxxxxxxxxxmtfxxxxxxxxxxxxxxxxxmtfxxxxxxxxxxxxxxxxxmtfxxxxxxxxx" +
      "xxxxxxxxmtfxxxxxxxxxxxxxxxxxmtfxxxxxxxxmtxtxxtxtmtxxxxxxtmtxmmtmxtttttxtxttxtmtxxxxxxmttxmxxx",
    "blo,cv,ksh,lag" to
      "zoxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "lv,prg" to
      "zoxxxxxxxxzzzzzzzzzzzoxxxxxxxxzoxxxxxxxxzoxxxxxxxxzoxxxxxxxxzoxxxxxxxxzoxxxxxxxxzoxxxxxxxxzox" +
      "xxxxxxxzoxxxxxxxxzzzzzzzzzzzoxxxxxxxxzzoxzxozxzxoxzzzzzxzoxzoozozzzzzzzzzzzxzoxzzzzxzoxzzozzz",
  )

  private val CLDR48_PT_PT_SIGNATURE: String =
    "xoxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" +
      "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxmxxxmxmmm"
}
