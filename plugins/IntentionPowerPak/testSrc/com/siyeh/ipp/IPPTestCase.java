/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.siyeh.ipp;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public abstract class IPPTestCase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("IntentionPowerPak") + "/test/com/siyeh/ipp/" + getRelativePath();
  }

  protected abstract String getRelativePath();

  protected void doTest() {
    doTest(getIntentionName());
  }

  protected void doTest(String intentionName) {
    final String testName = getTestName(false);
    CodeInsightTestUtil.doIntentionTest(myFixture, intentionName, testName + ".java", testName + "_after.java");
  }

  protected void assertIntentionNotAvailable() {
    assertIntentionNotAvailable(getIntentionName());
  }

  protected void assertIntentionNotAvailable(final String intentionName) {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".java");
    assertEmpty("Intention \'" + intentionName + "\' is available but should not",
                myFixture.filterAvailableIntentions(intentionName));
  }

  protected abstract String getIntentionName();
}
