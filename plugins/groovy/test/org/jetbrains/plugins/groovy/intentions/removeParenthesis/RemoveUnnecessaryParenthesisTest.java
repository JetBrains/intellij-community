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

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.01.2009
 */
public class RemoveUnnecessaryParenthesisTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/intentions/removeParenth/";
  }

  public void testRemoveUnnecessaryParenthesis() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses")));
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testNothingInsideClosure() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    assertEmpty(myFixture.filterAvailableIntentions("Remove Unnecessary Parentheses"));
  }
}
