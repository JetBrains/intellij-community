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

class MatcherTest : TestCase() {

  fun Matcher.assertIsMatching(fullyQualifiedMethodName: String, vararg params: String) {
    assertThat(isMatching(fullyQualifiedMethodName, listOf(*params))).isTrue()
  }

  fun Matcher.assertNotMatching(fullyQualifiedMethodName: String, vararg params: String) {
    assertThat(isMatching(fullyQualifiedMethodName, listOf(*params))).isFalse()
  }

  fun `test simple matcher`() {
    val matcher = MatcherConstructor.createMatcher("*.String(old*, new*)")!!

    matcher.assertIsMatching("java.lang.String", "oldValue", "newValue")
    matcher.assertIsMatching("java.lang.String", "old", "new")

    matcher.assertNotMatching("java.lang.String", "valueOld", "valueNew")
    matcher.assertNotMatching("Boolean", "old", "new")
  }

  fun `test any single param method matcher`() {
    val matchers = listOf(MatcherConstructor.createMatcher("*(*)")!!, MatcherConstructor.createMatcher("(*)")!!)
    
    matchers.forEach {
      it.assertIsMatching("java.lang.String.indexOf", "ch")
      it.assertIsMatching("java.lang.String.charAt", "index")
      it.assertIsMatching("java.lang.Boolean.valueOf", "value")
      it.assertNotMatching("java.lang.Boolean.substring", "from", "to")
    }
  }

  fun `test any with key value`() {
    val matcher = MatcherConstructor.createMatcher("*(key, value)")!!
    matcher.assertIsMatching("java.util.Map.put", "key", "value")
    matcher.assertIsMatching("java.util.HashMap.put", "key", "value")
    matcher.assertIsMatching("java.util.HashMap.putIfNeeded", "key", "value")
  }

  fun `test couple contains`() {
    val matcher = MatcherConstructor.createMatcher("*(first*, last*)")!!

    matcher.assertIsMatching("java.util.Str.subs", "firstIndex", "lastIndex")
    matcher.assertIsMatching("java.util.Str.subs", "first", "last")
  }

  fun `test setter matching`() {
    val matcher = MatcherConstructor.createMatcher("*.set*(*)")!!
    matcher.assertIsMatching("com.intellij.S.setProportion", "value")
    matcher.assertIsMatching("com.intellij.S.setX", "value")
    matcher.assertIsMatching("com.intellij.S.set", "height")
    
    matcher.assertNotMatching("com.intellij.S.set", "height", "weight")
    matcher.assertNotMatching("com.intellij.S.setSize", "height", "weight")
  }


}