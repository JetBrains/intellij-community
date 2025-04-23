// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang.test

import com.intellij.util.currentJavaVersion
import com.intellij.util.lang.JavaVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

class JavaVersionTest {
  @Test fun `1_0`(): Unit = doTest("1.0", 0)
  @Test fun `1_2`(): Unit = doTest("1.2", 2)
  @Test fun `1_4_0`(): Unit = doTest("1.4.0", 4)
  @Test fun `1_4_2_30`(): Unit = doTest("1.4.2_30", 4, 2, 30, 0)
  @Test fun `1_4_2_30-b03`(): Unit = doTest("1.4.2_30-b03", 4, 2, 30, 3)
  @Test fun `1_8_0_152`(): Unit = doTest("1.8.0_152", 8, 0, 152, 0)
  @Test fun `1_8_0_152-b16`(): Unit = doTest("1.8.0_152-b16", 8, 0, 152, 16)
  @Test fun `1_8_0_162-ea`(): Unit = doTest("1.8.0_162-ea", 8, 0, 162, 0, true)
  @Test fun `1_8_0_162-ea-b03`(): Unit = doTest("1.8.0_162-ea-b03", 8, 0, 162, 3, true)
  @Test fun `1_8_0_152-jb`(): Unit = doTest("1.8.0_152-release", 8, 0, 152, 0)
  @Test fun `1_8_0_152-jb-b13`(): Unit = doTest("1.8.0_152-release-1056-b13", 8, 0, 152, 13)
  @Test fun `1_8_0_151-debian-b12`(): Unit = doTest("1.8.0_151-8u151-b12-0ubuntu0.17.10.2-b12", 8, 0, 151, 12)
  @Test fun `1_8_0_45-internal`(): Unit = doTest("1.8.0_45-internal", 8, 0, 45, 0, true)
  @Test fun `1_8_0_121-2-whatever-b11`(): Unit = doTest("1.8.0_121-2-whatever-b11", 8, 0, 121, 11)
  @Test fun `1_8_0_121-(big-number)-b11`(): Unit = doTest("1.8.0_121-99${Long.MAX_VALUE}-b11", 8, 0, 121, 11)
  @Test fun `1_10`(): Unit = doTest("1.10", 10)

  @Test fun `5`(): Unit = doTest("5", 5)
  @Test fun `9`(): Unit = doTest("9", 9)
  @Test fun `9-ea`(): Unit = doTest("9-ea", 9, 0, 0, 0, true)
  @Test fun `9-internal`(): Unit = doTest("9-internal", 9, 0, 0, 0, true)
  @Test fun `9-ea+165`(): Unit = doTest("9-ea+165", 9, 0, 0, 165, true)
  @Test fun `9+181`(): Unit = doTest("9+181", 9, 0, 0, 181)
  @Test fun `9_0_1`(): Unit = doTest("9.0.1", 9, 0, 1, 0)
  @Test fun `9_1_2`(): Unit = doTest("9.1.2", 9, 1, 2, 0)
  @Test fun `9_0_1+11`(): Unit = doTest("9.0.1+11", 9, 0, 1, 11)
  @Test fun `9_0_3-ea`(): Unit = doTest("9.0.3-ea", 9, 0, 3, 0, true)
  @Test fun `9_0_3-ea+9`(): Unit = doTest("9.0.3-ea+9", 9, 0, 3, 9, true)
  @Test fun `9-Ubuntu`(): Unit = doTest("9-Ubuntu", 9)
  @Test fun `9-Ubuntu+0-9b181-4`(): Unit = doTest("9-Ubuntu+0-9b181-4", 9)
  @Test fun `10`(): Unit = doTest("10", 10)
  @Test fun `10-ea`(): Unit = doTest("10-ea", 10, 0, 0, 0, true)
  @Test fun `10-ea+36`(): Unit = doTest("10-ea+36", 10, 0, 0, 36, true)
  @Test fun `10-ea+0-valhalla`(): Unit = doTest("10-mvt_ea+0-2017-12-11-1328177.valhalla", 10)

  @Test fun empty(): Unit = doFailTest("")
  @Test fun spaces(): Unit = doFailTest("  ")
  @Test fun randomWord(): Unit = doFailTest("whatever")
  @Test fun incomplete1(): Unit = doFailTest("1")
  @Test fun incomplete2(): Unit = doFailTest("-1")
  @Test fun incomplete3(): Unit = doFailTest("1.")
  @Test fun outOfRange1(): Unit = doFailTest("0")
  @Test fun outOfRange2(): Unit = doFailTest("4")
  @Test fun outOfRange3(): Unit = doFailTest("99${Long.MAX_VALUE}")
  @Test fun ibmRt(): Unit = doFailTest("pxa6480sr3fp10-20160720_02 (SR3 FP10)")

  @Test fun current() {
    val current = currentJavaVersion()
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

  @Test fun formatting() {
    assertThat(JavaVersion.compose(8, 0, 0, 0, false).toString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 1, 0, 0, false).toString()).isEqualTo("1.8.1")
    assertThat(JavaVersion.compose(8, 0, 1, 0, false).toString()).isEqualTo("1.8.0_1")
    assertThat(JavaVersion.compose(8, 0, 0, 1, false).toString()).isEqualTo("1.8.0-b1")
    assertThat(JavaVersion.compose(8, 0, 0, 0, true).toString()).isEqualTo("1.8.0-ea")
    assertThat(JavaVersion.compose(8, 1, 2, 3, true).toString()).isEqualTo("1.8.1_2-ea-b3")
    assertThat(JavaVersion.compose(9, 0, 0, 0, false).toString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 1, 0, 0, false).toString()).isEqualTo("9.1")
    assertThat(JavaVersion.compose(9, 0, 1, 0, false).toString()).isEqualTo("9.0.1")
    assertThat(JavaVersion.compose(9, 0, 0, 1, false).toString()).isEqualTo("9+1")
    assertThat(JavaVersion.compose(9, 0, 0, 0, true).toString()).isEqualTo("9-ea")
    assertThat(JavaVersion.compose(9, 1, 2, 3, true).toString()).isEqualTo("9.1.2-ea+3")
  }

  @Test fun formattingFeatureMinorUpdate() {
    assertThat(JavaVersion.compose(8, 0, 0, 0, false).toFeatureMinorUpdateString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 1, 0, 0, false).toFeatureMinorUpdateString()).isEqualTo("1.8.1")
    assertThat(JavaVersion.compose(8, 0, 1, 0, false).toFeatureMinorUpdateString()).isEqualTo("1.8.0_1")
    assertThat(JavaVersion.compose(8, 0, 0, 1, false).toFeatureMinorUpdateString()).isEqualTo("1.8.0")
    assertThat(JavaVersion.compose(8, 0, 0, 0, true).toFeatureMinorUpdateString()).isEqualTo("1.8.0")
    assertThat(JavaVersion.compose(8, 1, 2, 3, true).toFeatureMinorUpdateString()).isEqualTo("1.8.1_2")
    assertThat(JavaVersion.compose(9, 0, 0, 0, false).toFeatureMinorUpdateString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 1, 0, 0, false).toFeatureMinorUpdateString()).isEqualTo("9.1")
    assertThat(JavaVersion.compose(9, 0, 1, 0, false).toFeatureMinorUpdateString()).isEqualTo("9.0.1")
    assertThat(JavaVersion.compose(9, 0, 0, 1, false).toFeatureMinorUpdateString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 0, 0, 0, true).toFeatureMinorUpdateString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 1, 2, 3, true).toFeatureMinorUpdateString()).isEqualTo("9.1.2")
  }

  @Test fun formattingProductOnly() {
    assertThat(JavaVersion.compose(8, 0, 0, 0, false).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 1, 0, 0, false).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 0, 1, 0, false).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 0, 0, 1, false).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 0, 0, 0, true).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(8, 1, 2, 3, true).toFeatureString()).isEqualTo("1.8")
    assertThat(JavaVersion.compose(9, 0, 0, 0, false).toFeatureString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 1, 0, 0, false).toFeatureString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 0, 1, 0, false).toFeatureString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 0, 0, 1, false).toFeatureString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 0, 0, 0, true).toFeatureString()).isEqualTo("9")
    assertThat(JavaVersion.compose(9, 1, 2, 3, true).toFeatureString()).isEqualTo("9")
  }

  private fun doTest(versionString: String, feature: Int, minor: Int = 0, update: Int = 0, build: Int = 0, ea: Boolean = false) {
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
      "openjdk $versionString",
      // `release` file (1.7+)
      "JAVA_VERSION=\"$versionString\"",
      // OpenJ9
      "AdoptOpenJDK (OpenJ9) version $versionString",
    ).forEach { assertThat(JavaVersion.parse(it)).describedAs(it).isEqualTo(expected) }
  }

  private fun doFailTest(versionString: String) {
    assertThatExceptionOfType(IllegalArgumentException::class.java)
      .isThrownBy { JavaVersion.parse(versionString) }
      .withMessage(versionString)
  }
}
