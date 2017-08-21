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

package org.jetbrains.plugins.groovy.intentions;

import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMethodToClosureTest extends GrIntentionTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertMethodToClosure/";
  }

  public void testMethodToClosure() {
    doMethodToClosureTest();
  }

  public void testStaticMethodToClosure()  {
    doMethodToClosureTest();
  }

  public void testClosureToMethod()  {
    doClosureToMethodTest();
  }

  public void testClosureWithImplicitParameterToMethod()  {
    doClosureToMethodTest();
  }

  public void testClosureWithoutModifiersToMethod() {
    doClosureToMethodTest();
  }

  public void testClosureToMethodWithFieldUsages() {
    doClosureToMethodTest();
  }

  public void testMethodToClosureWithMemberPointer() {
    doMethodToClosureTest();
  }

  public void testMethodFromReference() {
    doMethodToClosureTest();
  }

  public void testConstructorToClosure() { doMethodToClosureTest(false); }

  public void testInvalidMethodName() {doMethodToClosureTest(false);}

  private void doClosureToMethodTest() {
    doTest(GroovyIntentionsBundle.message("convert.closure.to.method.intention.name"), true);
  }

  private void doMethodToClosureTest() {
    doMethodToClosureTest(true);
  }

  private void doMethodToClosureTest(boolean available) {
    doTest(GroovyIntentionsBundle.message("convert.method.to.closure.intention.name"), available);
  }
}
