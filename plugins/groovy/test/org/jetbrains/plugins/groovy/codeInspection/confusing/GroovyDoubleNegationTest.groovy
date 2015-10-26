/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.confusing

import com.intellij.codeInspection.InspectionProfileEntry
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

public class GroovyDoubleNegationTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyDoubleNegationInspection()] }

  void testBoolean() {
    shouldRegisterError(/!!true/)
    shouldRegisterError(/!!Boolean.FALSE/)
    shouldRegisterError(/!!bool/)
    shouldRegisterError(/!!boolFun()/)
    shouldRegisterError(/!(1 != 2)/)
  }

  void testNonBoolean() {
    shouldNotRegisterError(/!!1/)
    shouldNotRegisterError(/!!1.0/)
    shouldNotRegisterError(/!![]/)
    shouldNotRegisterError(/!![1]/)
    shouldNotRegisterError(/!!''/)
    shouldNotRegisterError(/!!''.toString()/)
    shouldNotRegisterError(/!!null/)
  }

  private void shouldRegisterError(@Language('Groovy') String text) {
    def message = "Double negation " + text
    testHighlighting """
         def bool = (1 == 2)
         def boolFun() { 1 == 2 }
     
         <warning descr="$message">$text</warning>
       """
  }

  private void shouldNotRegisterError(@Language('Groovy') String text) {
    testHighlighting text
  }
}