/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase

class GradleNameElementUtilTest : UsefulTestCase() {
  // These tests check that we have correctly implemented the isomorphism between a list of unescaped strings and a single string with
  // a delimiter to separate suitably-escaped parts.
  fun testSplitNoDots() {
    assertThat(GradleNameElementUtil.split("abcde")).isEqualTo(listOf("abcde"))
  }

  fun testSplitDot() {
    assertThat(GradleNameElementUtil.split("ab.de")).isEqualTo(listOf("ab", "de"))
  }

  fun testSplitMultipleDots() {
    assertThat(GradleNameElementUtil.split("ab.de.gh")).isEqualTo(listOf("ab", "de", "gh"))
  }

  fun testSplitMultipleConsecutiveDots() {
    assertThat(GradleNameElementUtil.split("ab..ef")).isEqualTo(listOf("ab", "", "ef"))
  }

  fun testSplitDotAtBeginning() {
    assertThat(GradleNameElementUtil.split(".bcdef")).isEqualTo(listOf("", "bcdef"))
  }

  fun testSplitDotAtEnd() {
    assertThat(GradleNameElementUtil.split("abcde.")).isEqualTo(listOf("abcde", ""))
  }

  fun testSplitEscapedDot() {
    assertThat(GradleNameElementUtil.split("""ab\.de""")).isEqualTo(listOf("ab.de"))
  }

  fun testSplitEscapedBackslash() {
    assertThat(GradleNameElementUtil.split("""ab\\de""")).isEqualTo(listOf("""ab\de"""))
  }

  fun testSplitExoticCharacters() {
    assertThat(GradleNameElementUtil.split("ab \t \n é Å £ € \u1234")).isEqualTo(listOf("ab \t \n é Å £ € \u1234"))
  }

  fun testJoinNoDots() {
    assertThat(GradleNameElementUtil.join(listOf("abcde"))).isEqualTo("abcde")
  }

  fun testJoinDot() {
    assertThat(GradleNameElementUtil.join(listOf("ab", "de"))).isEqualTo("ab.de")
  }

  fun testJoinMultipleDots() {
    assertThat(GradleNameElementUtil.join(listOf("ab", "de", "gh"))).isEqualTo("ab.de.gh")
  }

  fun testJoinMultipleConsecutiveDots() {
    assertThat(GradleNameElementUtil.join(listOf("ab", "", "ef"))).isEqualTo("ab..ef")
  }

  fun testJoinDotAtBeginning() {
    assertThat(GradleNameElementUtil.join(listOf("", "bcdef"))).isEqualTo(".bcdef")
  }

  fun testJoinDotAtEnd() {
    assertThat(GradleNameElementUtil.join(listOf("abcde", ""))).isEqualTo("abcde.")
  }

  fun testJoinEscapedDot() {
    assertThat(GradleNameElementUtil.join(listOf("ab.de"))).isEqualTo("""ab\.de""")
  }

  fun testJoinEscapedBackslash() {
    assertThat(GradleNameElementUtil.join(listOf("""ab\de"""))).isEqualTo("""ab\\de""")
  }

  fun testJoinExoticCharacters() {
    assertThat(GradleNameElementUtil.join(listOf("ab \t \n é Å £ € \u1234"))).isEqualTo("ab \t \n é Å £ € \u1234")
  }

  // These tests check that we have implemented the lower-level escaping and unescaping operations correctly, which are occasionally
  // needed by backends.
  fun testEscapeNoDot() {
    assertThat(GradleNameElementUtil.escape("abcde")).isEqualTo("abcde")
  }

  fun testEscapeDot() {
    assertThat(GradleNameElementUtil.escape("ab.de")).isEqualTo("""ab\.de""")
  }

  fun testEscapeMultipleDots() {
    assertThat(GradleNameElementUtil.escape("ab.de.gh")).isEqualTo("""ab\.de\.gh""")
  }

  fun testEscapeMultipleConsecutiveDots() {
    assertThat(GradleNameElementUtil.escape("ab..ef")).isEqualTo("""ab\.\.ef""")
  }

  fun testEscapeDotAtBeginning() {
    assertThat(GradleNameElementUtil.escape(".bcdef")).isEqualTo("""\.bcdef""")
  }

  fun testEscapeDotAtEnd() {
    assertThat(GradleNameElementUtil.escape("abcde.")).isEqualTo("""abcde\.""")
  }

  fun testEscapeEscapedDot() {
    assertThat(GradleNameElementUtil.escape("""ab\.de""")).isEqualTo("""ab\\\.de""")
  }

  fun testEscapeEscapedBackslash() {
    assertThat(GradleNameElementUtil.escape("""ab\\de""")).isEqualTo("""ab\\\\de""")
  }

  fun testUnescapeNoDot() {
    assertThat(GradleNameElementUtil.unescape("abcde")).isEqualTo("abcde")
  }

  fun testUnescapeDot() {
    assertThat(GradleNameElementUtil.unescape("""ab\.de""")).isEqualTo("ab.de")
  }

  fun testUnescapeMultipleDots() {
    assertThat(GradleNameElementUtil.unescape("""ab\.de\.gh""")).isEqualTo("ab.de.gh")
  }

  fun testUnescapeMultipleConsecutiveDots() {
    assertThat(GradleNameElementUtil.unescape("""ab\.\.ef""")).isEqualTo("ab..ef")
  }

  fun testUnescapeDotAtBeginning() {
    assertThat(GradleNameElementUtil.unescape("""\.bcdef""")).isEqualTo(".bcdef")
  }

  fun testUnescapeDotAtEnd() {
    assertThat(GradleNameElementUtil.unescape("""abcde\.""")).isEqualTo("abcde.")
  }

  fun testUnescapeEscapedEscapedDot() {
    assertThat(GradleNameElementUtil.unescape("""ab\\\.de""")).isEqualTo("""ab\.de""")
  }

  fun testUnescapeEscapedEscapedBackslash() {
    assertThat(GradleNameElementUtil.unescape("""ab\\\\de""")).isEqualTo("""ab\\de""")
  }
}