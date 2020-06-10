/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints.settings


import com.intellij.codeInsight.hints.getExcludeListInvalidLineNumbers
import com.intellij.codeInsight.hints.getHintProviders
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

class ExcludeListCorrectnessTest: LightPlatformTestCase() {
  
  fun `test check all blacklists are valid`() {
    val providers = getHintProviders()
    providers.forEach {
      val blacklist = it.second.defaultBlackList
      val invalidLineNumbers = getExcludeListInvalidLineNumbers(blacklist.joinToString("\n"))
      
      TestCase.assertTrue(
        "Blacklist pattern error detected in ${it.first.displayName} implementation: ${getInvalidElements(blacklist, invalidLineNumbers)}", 
        invalidLineNumbers.isEmpty()
      )
    }
  }
  
  private fun getInvalidElements(blacklist: Set<String>, invalidLineNumbers: List<Int>): List<String> {
    val list = blacklist.toList()
    return invalidLineNumbers.map { list[it] }
  }
  
}