/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionActionWrapper;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public abstract class IPPTestCase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("IntentionPowerPak") + "/test/com/siyeh/ipp/" + getRelativePath();
  }

  @NonNls
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

  protected void assertIntentionNotAvailable(Class<? extends IntentionAction> intentionClass) {
    myFixture.configureByFile(getTestName(false) + ".java");
    final List<IntentionAction> result = new SmartList<IntentionAction>();
    for (final IntentionAction intention : myFixture.getAvailableIntentions()) {
      if (intentionClass.isInstance(intention)) {
        result.add(intention);
      }
      else if (intention instanceof IntentionActionWrapper) {
        final IntentionActionWrapper wrapper = (IntentionActionWrapper)intention;
        if (intentionClass.isInstance(wrapper.getDelegate())) {
          result.add(intention);
        }
      }
    }
    assertEmpty("Intention of class \'" + intentionClass + "\' is available but should not", result);
  }

  protected abstract String getIntentionName();
}
