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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;

import java.util.List;

public abstract class AbstractJavaFXQuickFixTest extends AbstractJavaFXTestCase {
  protected abstract String getHint(String tagName);

  protected void doLaunchQuickfixTest(final String tagName) {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    final IntentionAction singleIntention = myFixture.findSingleIntention(getHint(tagName));
    assertNotNull(singleIntention);
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(true) + "_after.fxml");
  }

  protected void checkQuickFixNotAvailable(String tagName) {
    myFixture.configureByFiles(getTestName(true) + ".fxml");
    final List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    final IntentionAction intention = CodeInsightTestUtil.findIntentionByText(intentions, getHint(tagName));
    assertNull(intention);
  }
}
