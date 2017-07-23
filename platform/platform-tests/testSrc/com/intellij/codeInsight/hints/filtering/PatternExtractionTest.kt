/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.hints.filtering

import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

class PatternExtractionTest : TestCase() {

  fun String.assertMatcher(nameMatcher: String, paramsMatcher: String) {
    val matcher = MatcherConstructor.extract(this)
    if (matcher == null) {
      assertThat(nameMatcher).isEmpty()
      assertThat(paramsMatcher).isEmpty()
      return
    }

    assertThat(nameMatcher).isEqualTo(matcher.first)
    assertThat(paramsMatcher).isEqualTo(matcher.second)
  }
  
  fun String.assertError() {
    val matcher = MatcherConstructor.extract(this)
    assertThat(matcher).isNull()
  }
  
  fun `test error when no closing brace`() {
    val text = "Test.foo(paramName"
    text.assertError()
  }

  fun `test match all methods from package`() {
    val text = "java.lang.*"
    text.assertMatcher("java.lang.*", "")
  }

  fun `test match all methods from class String`() {
    val text = "java.lang.String.*"
    text.assertMatcher("java.lang.String.*", "")
  }

  fun `test match all replace methods`() {
    val text = "*.replace"
    text.assertMatcher("*.replace", "")
  }

  fun `test match all replace methods with couple params`() {
    val text = "*.replace(*, *)"
    text.assertMatcher("*.replace", "(*, *)")
  }

  fun `test match only one replace method with couple params`() {
    val text = "java.lang.String.replace(*,*)"
    text.assertMatcher("java.lang.String.replace", "(*,*)")
  }

  fun `test match debug particular method`() {
    val text = "org.logger.Log.debug(format,arg)"
    text.assertMatcher("org.logger.Log.debug", "(format,arg)")
  }

  fun `test match method with single param`() {
    val text = "class.method(*)"
    text.assertMatcher("class.method", "(*)")
  }

  fun `test match all methods with single param`() {
    val text = "*(*)"
    text.assertMatcher("*", "(*)")
  }

  fun `test simplified params matcher`() {
    val text = "(*)"
    text.assertMatcher("", "(*)")
  }

  fun `test shit`() {
    val text = "            foooo      (*)      "
    text.assertMatcher("foooo", "(*)")
  }

}