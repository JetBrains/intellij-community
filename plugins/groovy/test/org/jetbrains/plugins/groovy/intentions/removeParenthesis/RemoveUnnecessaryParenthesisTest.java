/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.intentions.removeParenthesis;

import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class RemoveUnnecessaryParenthesisTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/removeParenth/";
  }

  public void testRemoveUnnecessaryParenthesis() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses")));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testNothingInsideClosure() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    assertEmpty(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses"));
  }

  public void testNamedArgs() {
    doTest();
  }
}
