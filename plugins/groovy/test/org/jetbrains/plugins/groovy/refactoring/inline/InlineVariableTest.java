/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;

/**
 * @author ilyas
 */
public class InlineVariableTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/refactoring/inlineLocal/";
  }

  public void testGRVY_1232() throws Throwable { doTest(); }
  public void testGRVY_1248() throws Throwable { doTest(); }
  public void testVar1() throws Throwable { doTest(); }
  public void testVar2() throws Throwable { doTest(); }
  public void testVar3() throws Throwable { doTest(); }
  public void testVar4() throws Throwable { doTest(); }
  public void testVar5() throws Throwable { doTest(); }
  public void testVar6() throws Throwable { doTest(); }

  protected void doTest() throws IncorrectOperationException, InvalidDataException, IOException {
    InlineMethodTest.doInlineTest(myFixture, getTestDataPath() + getTestName(true) + ".test", true);
  }

}
