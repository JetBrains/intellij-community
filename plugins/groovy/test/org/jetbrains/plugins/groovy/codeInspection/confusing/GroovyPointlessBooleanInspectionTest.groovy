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

public class GroovyPointlessBooleanInspectionTest extends GrHighlightingTestBase {
  @Override
  InspectionProfileEntry[] getCustomInspections() { [new GroovyPointlessBooleanInspection()] }

  void testSimplifyWhenDoubleNegationPrefix() {
    shouldSimplifyDoubleNegation(/!!true/)
    shouldSimplifyDoubleNegation(/!!false/)
  }

  void testSimplifyNegatedBoolean() {
    shouldSimplifyWithOppositeBoolean(/!true/)
    shouldSimplifyWithOppositeBoolean(/!false/)
  }

  private void shouldSimplifyDoubleNegation(@Language('Groovy') String text) {
    def simplifiedText = text.replace('!', '')
    assertSimplification(text, simplifiedText)
  }

  private void shouldSimplifyWithOppositeBoolean(@Language('Groovy') String text) {
    def operand = text.replace('!', '')
    def simplifiedText = "true".equals(operand) ? "false"
                                                : "true";
    assertSimplification(text, simplifiedText)
  }

  private assertSimplification(String text, String simplifiedText) {
    testHighlighting """
         <warning descr="$text can be simplified to '$simplifiedText'">$text</warning>
       """
  }
}
