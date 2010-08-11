/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
abstract public class GroovyCompletionTestBase extends LightCodeInsightFixtureTestCase {
  protected void doSmartTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
  }

  protected void doBasicTest() {
    myFixture.testCompletion(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy");
  }

  public void doVariantableTest(String... variants) throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.BASIC);
    assertOrderedEquals(myFixture.getLookupElementStrings(), variants);
  }

  public void doSmartCompletion(String... variants) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    final List<String> list = myFixture.getLookupElementStrings();
    assertNotNull(list);
    UsefulTestCase.assertSameElements(list, variants);
  }
}
