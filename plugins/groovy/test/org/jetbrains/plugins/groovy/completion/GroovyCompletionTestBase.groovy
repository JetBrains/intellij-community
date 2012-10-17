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
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.GroovyFileType
/**
 * @author Maxim.Medvedev
 */
abstract public class GroovyCompletionTestBase extends LightCodeInsightFixtureTestCase {
  protected void doSmartTest() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    checkResult();
  }

  protected void doSmartTest(String before, String after) {
    myFixture.configureByText('_a.groovy', before)
    myFixture.complete(CompletionType.SMART)
    assertNull(myFixture.lookupElements)
    myFixture.checkResult(after)
  }

  protected void checkResult() {
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
  }

  protected void doBasicTest(String type = "") {
    myFixture.testCompletionTyping(getTestName(false) + ".groovy", type, getTestName(false) + "_after.groovy");
  }

  protected void doBasicTest(String before, String after) {
    myFixture.configureByText('_a.groovy', before)
    myFixture.completeBasic()
    assertNull(myFixture.lookupElements)
    myFixture.checkResult(after)
  }


  public void doVariantableTest(String... variants) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.BASIC);
    assertOrderedEquals(myFixture.lookupElementStrings, variants);
  }

  public void doHasVariantsTest(String... variants) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.BASIC);
    if (!myFixture.lookupElementStrings.containsAll(variants)) {
      assertOrderedEquals(myFixture.lookupElementStrings, variants)
    }
  }

  public void doSmartCompletion(String... variants) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.complete(CompletionType.SMART);
    final List<String> list = myFixture.lookupElementStrings;
    assertNotNull(list);
    assertOrderedEquals(list, variants);
  }

  void doSmartVariantableTest(String before, String... variants) {
    myFixture.configureByText(getTestName(false) + ".groovy", before);
    myFixture.complete(CompletionType.SMART);
    final List<String> list = myFixture.lookupElementStrings;
    assertNotNull(list);
    assertOrderedEquals(list, variants);
  }


  public void checkCompletion(String before, String type, String after) {
    myFixture.configureByText("a.groovy", before);
    myFixture.completeBasic();
    myFixture.type(type);
    myFixture.checkResult(after);
  }

  public void checkSingleItemCompletion(String before, String after) {
    myFixture.configureByText("a.groovy", before);
    assert !myFixture.completeBasic();
    myFixture.checkResult(after);
  }

  public void doNoVariantsTest(String before, String... excludedVariants) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before)
    myFixture.completeBasic()
    final excluded = ContainerUtil.newHashSet(excludedVariants)
    for (String lookup : myFixture.lookupElementStrings) {
      assertFalse(lookup, excluded.contains(lookup))
    }
  }

  protected static def caseSensitiveNone() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
  }

}
