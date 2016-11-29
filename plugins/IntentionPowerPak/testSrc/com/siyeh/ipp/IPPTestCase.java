/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class IPPTestCase extends LightCodeInsightFixtureTestCase {

  private static final Pattern PATTERN = Pattern.compile("/\\*_(.*)\\*/");

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("IntentionPowerPak") + "/test/com/siyeh/ipp/" + getRelativePath();
  }

  @NonNls
  protected String getRelativePath() {
    return "";
  }

  protected void doTest() {
    doTest(getIntentionName());
  }

  protected void doTest(@NotNull String intentionName) {
    final String testName = getTestName(false);
    CodeInsightTestUtil.doIntentionTest(myFixture, intentionName, testName + ".java", testName + "_after.java");
  }

  protected void doTest(@NotNull @Language("JAVA") String before, @NotNull @Language("JAVA") String after) {
    final Matcher matcher = PATTERN.matcher(before);
    Assert.assertTrue("No caret and intention name specified", matcher.find());
    myFixture.configureByText("a.java", matcher.replaceFirst("<caret>"));
    myFixture.launchAction(myFixture.findSingleIntention(matcher.group(1)));
    myFixture.checkResult(after);
  }

  protected void assertIntentionNotAvailable() {
    assertIntentionNotAvailable(getIntentionName());
  }

  protected void assertIntentionNotAvailable(@NotNull final String intentionName) {
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".java");
    assertEmpty("Intention \'" + intentionName + "\' is available but should not",
                myFixture.filterAvailableIntentions(intentionName));
  }

  protected void assertIntentionNotAvailable(Class<? extends IntentionAction> intentionClass) {
    myFixture.configureByFile(getTestName(false) + ".java");
    final List<IntentionAction> result = new SmartList<>();
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

  protected String getIntentionName() {
    return null;
  }
}
