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
package org.jetbrains.plugins.groovy.intentions;

/**
 * @author Niels Harremoes
 * @author Oscar Toernroth
 */
public class SimplifyTernaryOperatorTest extends GrIntentionTestCase {
  private static final String INTENTION_NAME = GroovyIntentionsBundle.message("simplify.ternary.operator.intention.name");

  private void doTest(String before, String after) {

    doTextTest(before, INTENTION_NAME, after);
  }

  public void testDoNotTriggerOnNormalConditional() {
    doAntiTest("aaa ?<caret> bbb : ccc", INTENTION_NAME);
    doAntiTest("aaa ?<caret> false : ccc", INTENTION_NAME);
    doAntiTest("aaa ?<caret> bbb : true", INTENTION_NAME);
  }

  public void test_don_t_trigger_for_non_boolean_conditions() {
    doAntiTest("""
                 def a = 0
                 def b = 2
                 println a <caret>? true : b""", INTENTION_NAME);
  }

  public void testSimplifyWhenThenIsTrue() {
    doTest("aaa ?<caret> true: bbb", "aaa ||<caret> bbb");
  }

  public void testSimplifyWhenThenIsTrueComplexArguments() {
    doTest("aaa ?<caret> true : bbb ? ccc : ddd", "aaa || (bbb ? ccc : ddd)");
  }

  public void testSimplifyWhenElseIsFalseComplexArguments() {
    doTest("aaa || bbb ? ccc || ddd : false", "(aaa || bbb) && (ccc || ddd)");
  }

  public void testSimplifyWhenElseIsFalse() {
    doTest("aaa ?<caret> bbb : false", "aaa &&<caret> bbb");
  }

  public void testResultIsWrappedInParenthesesWhenNeeded() {
    doTest("a ?<caret> b ? true : d : false", "a &&<caret> (b ? true : d)");
  }
}
