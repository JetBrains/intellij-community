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

class StringMatchingTest : TestCase() {

  fun com.intellij.codeInsight.hints.filtering.StringMatcher.assertMatches(vararg matched: String) {
    matched.forEach { assertThat(isMatching(it)).isTrue() }
  }

  fun com.intellij.codeInsight.hints.filtering.StringMatcher.assertNotMatches(vararg unmatched: String) {
    unmatched.forEach { assertThat(isMatching(it)).isFalse() }
  }
  
  fun `test simple`() {
    val matcher = StringMatcherBuilder.create("aaa")!!
    
    matcher.assertMatches("aaa")
    matcher.assertNotMatches("aaaa", "aab", "", "*", "a", "baaa")
  }
  
  fun `test asterisks before`() {
    val matcher = StringMatcherBuilder.create("aaa*")!!
    
    matcher.assertMatches("aaa", "aaaa", "aaaaaa", "aaaqwe")
    matcher.assertNotMatches("baaa", "nnaaa", "qweaaa")
  }

  fun `test asterisks after`() {
    val matcher = StringMatcherBuilder.create("*aaa")!!
    
    matcher.assertMatches("aaa", "aaaa", "baaa", "aawweraaa")
    matcher.assertNotMatches("aaab", "aaabaa")
  }
  
  fun `test multiple asterisks`() {
    val matcher = StringMatcherBuilder.create("*aax*")!!
    matcher.assertMatches("qaaxq", "qqaaxqqq")
    matcher.assertNotMatches("ax", "axx")
  }
  
  
}