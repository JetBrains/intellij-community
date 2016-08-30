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
package org.jetbrains.plugins.groovy.intentions

/**
 * @author Niels Harremoes
 * @author Oscar Toernroth
 */
class SimplifyTernaryOperatorTest extends GrIntentionTestCase {

  String intentionName = GroovyIntentionsBundle.message("simplify.ternary.operator.intention.name")

  private void doTest(String before, String after) {

    doTextTest before, intentionName, after
  }

  void testDoNotTriggerOnNormalConditional() throws Exception {
    doAntiTest 'aaa ?<caret> bbb : ccc', intentionName
    doAntiTest 'aaa ?<caret> false : ccc', intentionName
    doAntiTest 'aaa ?<caret> bbb : true', intentionName
  }

  void "test don't trigger for non-boolean conditions"() throws Exception {
    doAntiTest 'def a = 0\n' +
               'def b = 2\n' +
               'println a <caret>? true : b', intentionName
  }


  void testSimplifyWhenThenIsTrue() throws Exception {
    doTest 'aaa ?<caret> true: bbb', 'aaa ||<caret> bbb'
  }


  void testSimplifyWhenThenIsTrueComplexArguments() throws Exception {
    doTest 'aaa ?<caret> true : bbb ? ccc : ddd', 'aaa || (bbb ? ccc : ddd)'
  }

  void testSimplifyWhenElseIsFalseComplexArguments() throws Exception {
    doTest 'aaa || bbb ? ccc || ddd : false', '(aaa || bbb) && (ccc || ddd)'
  }

  void testSimplifyWhenElseIsFalse() throws Exception {
    doTest 'aaa ?<caret> bbb : false', 'aaa &&<caret> bbb'
  }

  // aaa ? false : bbb -> (!aaa) && bbb

  void testResultisWrappedInParenthesesWhenNeeded() throws Exception {
    doTest 'a ?<caret> b ? true : d : false', 'a &&<caret> (b ? true : d)'
  }


}
