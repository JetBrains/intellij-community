/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.lang

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class JavaVersionTest {
  @Test fun `1_0`() = doTest("1.0", 0)
  @Test fun `1_2`() = doTest("1.2", 2)
  @Test fun `1_4_0`() = doTest("1.4.0", 4)
  @Test fun `1_4_2_30`() = doTest("1.4.2_30", 4, 2, 30, 0)
  @Test fun `1_4_2_30-b03`() = doTest("1.4.2_30-b03", 4, 2, 30, 3)
  @Test fun `1_8_0_152`() = doTest("1.8.0_152", 8, 0, 152, 0)
  @Test fun `1_8_0_152-b16`() = doTest("1.8.0_152-b16", 8, 0, 152, 16)
  @Test fun `1_8_0_162-ea`() = doTest("1.8.0_162-ea", 8, 0, 162, 0, true)
  @Test fun `1_8_0_162-ea-b03`() = doTest("1.8.0_162-ea-b03", 8, 0, 162, 3, true)
  @Test fun `1_8_0_152-jb`() = doTest("1.8.0_152-release", 8, 0, 152, 0)
  @Test fun `1_8_0_152-jb-b13`() = doTest("1.8.0_152-release-1056-b13", 8, 0, 152, 13)
  @Test fun `1_8_0_151-debian-b12`() = doTest("1.8.0_151-8u151-b12-0ubuntu0.17.10.2-b12", 8, 0, 151, 12)
  @Test fun `1_8_0_45-internal`() = doTest("1.8.0_45-internal", 8, 0, 45, 0, true)
  @Test fun `1_8_0_121-2-whatever-b11`() = doTest("1.8.0_121-2-whatever-b11", 8, 0, 121, 11)
  @Test fun `1_8_0_121-(big-number)-b11`() = doTest("1.8.0_121-99${Long.MAX_VALUE}-b11", 8, 0, 121, 11)
  @Test fun `1_10`() = doTest("1.10", 10)

  @Test fun `9`() = doTest("9", 9)
  @Test fun `9-ea`() = doTest("9-ea", 9, 0, 0, 0, true)
  @Test fun `9-internal`() = doTest("9-internal", 9, 0, 0, 0, true)
  @Test fun `9-ea+165`() = doTest("9-ea+165", 9, 0, 0, 165, true)
  @Test fun `9+181`() = doTest("9+181", 9, 0, 0, 181)
  @Test fun `9_0_1`() = doTest("9.0.1", 9, 0, 1, 0)
  @Test fun `9_1_2`() = doTest("9.1.2", 9, 1, 2, 0)
  @Test fun `9_0_1+11`() = doTest("9.0.1+11", 9, 0, 1, 11)
  @Test fun `9_0_3-ea`() = doTest("9.0.3-ea", 9, 0, 3, 0, true)
  @Test fun `9_0_3-ea+9`() = doTest("9.0.3-ea+9", 9, 0, 3, 9, true)
  @Test fun `9-Ubuntu`() = doTest("9-Ubuntu", 9)
  @Test fun `9-Ubuntu+0-9b181-4`() = doTest("9-Ubuntu+0-9b181-4", 9)
  @Test fun `10`() = doTest("10", 10)
  @Test fun `10-ea`() = doTest("10-ea", 10, 0, 0, 0, true)
  @Test fun `10-ea+36`() = doTest("10-ea+36", 10, 0, 0, 36, true)
  @Test fun `10-ea+0-valhalla`() = doTest("10-mvt_ea+0-2017-12-11-1328177.valhalla", 10)

  @Test fun empty() = doFailTest("")
  @Test fun spaces() = doFailTest("  ")
  @Test fun randomWord() = doFailTest("whatever")
  @Test fun incomplete1() = doFailTest("1")
  @Test fun incomplete2() = doFailTest("-1")
  @Test fun incomplete3() = doFailTest("1.")
  @Test fun outOfRange1() = doFailTest("0")
  @Test fun outOfRange2() = doFailTest("5")
  @Test fun outOfRange3() = doFailTest("99${Long.MAX_VALUE}")
  @Test fun ibmRt() = doFailTest("pxa6480sr3fp10-20160720_02 (SR3 FP10)")

  @Test fun current() {
    val current = JavaVersion.current()
    assertThat(current.feature).isGreaterThanOrEqualTo(8)
    assertThat(current.minor).isEqualTo(0)
    assertThat(current.build).isGreaterThan(0)
  }

  @Test fun comparing() {
    assertThat(JavaVersion.compose(9, 0, 0, 0, false)).isGreaterThan(JavaVersion.compose(8, 0, 0, 0, false))
    assertThat(JavaVersion.compose(8, 1, 0, 0, false)).isGreaterThan(JavaVersion.compose(8, 0, 0, 0, false))
    assertThat(JavaVersion.compose(8, 0, 1, 0, false)).isGreaterThan(JavaVersion.compose(8, 0, 0, 0, false))
    assertThat(JavaVersion.compose(8, 0, 0, 1, false)).isGreaterThan(JavaVersion.compose(8, 0, 0, 0, false))
    assertThat(JavaVersion.compose(8, 0, 0, 0, false)).isGreaterThan(JavaVersion.compose(8, 0, 0, 0, true))
  }

  private fun doTest(versionString: String,
                     feature: Int,
                     minor: Int = 0,
                     update: Int = 0,
                     build: Int = 0,
                     ea: Boolean = false) {
    val expected = JavaVersion.compose(feature, minor, update, build, ea)

    val parsed = JavaVersion.parse(versionString)
    assertThat(parsed).isEqualTo(expected)
    val reparsed = JavaVersion.parse(parsed.toString())
    assertThat(reparsed).isEqualTo(expected)

    sequenceOf(
      // "java -version", 1st line
      "java version \"$versionString\"",                                          // 1.0 - 9
      "openjdk version \"$versionString\"",
      "java version \"$versionString\" 2018-03-20",                               // 10+
      "openjdk version \"$versionString\" 2018-03-20",
      // "java -version", 2nd line
      "Classic VM (build JDK-$versionString, native threads, jit)",               // 1.2
      "Java(TM) 2 Runtime Environment, Standard Edition (build $versionString)",  // 1.3 - 1.5
      "Java(TM) SE Runtime Environment (build $versionString)",                   // 1.6 - 9
      "OpenJDK Runtime Environment (build $versionString)",
      "Java(TM) SE Runtime Environment 18.3 (build $versionString)",              // 10+
      "OpenJDK Runtime Environment 18.3 (build $versionString)",
      // "java --full-version" (9+)
      "java $versionString",
      "openjdk $versionString"
    ).forEach { assertThat(JavaVersion.parse(it)).describedAs(it).isEqualTo(expected) }
  }

  private fun doFailTest(versionString: String) {
    assertThatExceptionOfType(IllegalArgumentException::class.java)
      .isThrownBy { JavaVersion.parse(versionString) }
      .withMessage(versionString)
  }
}