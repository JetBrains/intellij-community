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

package org.jetbrains.plugins.groovy;

import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.codeInsight.completion.CompletionType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Medvedev
 * Date: Feb 27, 2009
 * Time: 5:06:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class GroovyCompletionTest extends CodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/completion/";
  }

  public void testSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    //myFixture.testCompletionVariants();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "Bar", "Foo");
    
    //myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testSmartCompletionAfterNewInDeclarationWithInterface() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testCaretAfterSmartCompletionAfterNewInDeclaration() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    //assertOrderedEquals(myFixture.getLookupElementStrings(), "Bar");
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }
  
  public void testSmartCompletionAfterNewInDeclarationWithAbstractClass() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }
}
