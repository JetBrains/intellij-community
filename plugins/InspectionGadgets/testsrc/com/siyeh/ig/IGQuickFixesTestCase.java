/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * @author anna
 * @since 16-Jun-2009
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
public abstract class IGQuickFixesTestCase extends JavaCodeInsightFixtureTestCase {
  protected String myDefaultHint = null;
  protected String myRelativePath = null;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    for (String environmentClass : getEnvironmentClasses()) {
      myFixture.addClass(environmentClass);
    }
    myFixture.enableInspections(getInspections());
  }

  protected BaseInspection getInspection() {
    return null;
  }

  protected BaseInspection[] getInspections() {
    final BaseInspection inspection = getInspection();
    if (inspection != null) {
      return new BaseInspection[] {inspection};
    }
    return new BaseInspection[0];
  }

  @NonNls
  @Language("JAVA")
  protected String[] getEnvironmentClasses() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test/com/siyeh/igfixes/";
  }

  protected void assertQuickfixNotAvailable() {
    assertQuickfixNotAvailable(myDefaultHint);
  }

  protected void assertQuickfixNotAvailable(final String quickfixName) {
    final String testName = getTestName(false);
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    assertEmpty("Quickfix \'" + quickfixName + "\' is available but should not",
                myFixture.filterAvailableIntentions(quickfixName));
  }

  protected void doTest() {
    assertNotNull(myDefaultHint);
    final String testName = getTestName(false);
    doTest(testName, myDefaultHint);
  }

  protected void doTest(String hint) {
    final String testName = getTestName(false);
    doTest(testName, hint);
  }

  protected void doTest(final String testName, final String hint) {
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    final IntentionAction action = findIntention(hint);
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
  }

  public IntentionAction findIntention(@NotNull final String hint) {
    final List<IntentionAction> availableIntentions =
      ContainerUtil.findAll(myFixture.getAvailableIntentions(), new Condition<IntentionAction>() {
        @Override
        public boolean value(final IntentionAction intentionAction) {
          return intentionAction instanceof QuickFixWrapper;
        }
      });
    final List<IntentionAction> list = ContainerUtil.findAll(availableIntentions, new Condition<IntentionAction>() {
      @Override
      public boolean value(IntentionAction intentionAction) {
        return intentionAction.getText().equals(hint);
      }
    });
    if (list.isEmpty()) {
      Assert.fail("\"" + hint + "\" not in " + list);
    }
    else if (list.size() > 1) {
      Assert.fail("Too many quickfixes found for \"" + hint + "\": " + list + "]");
    }
    return list.get(0);
  }


  protected void doExpressionTest(
    String hint,
    @Language(value = "JAVA", prefix = "class $X$ {{System.out.print(", suffix = ");}}") @NotNull @NonNls String before,
    @Language(value = "JAVA", prefix = "class $X$ {{System.out.print(", suffix = ");}}") @NotNull @NonNls String after) {
    doTest(hint, "class $X$ {{System.out.print(" + before + ");}}", "class $X$ {{System.out.print(" + after + ");}}");
  }

  protected void doMemberTest(
    String hint,
    @Language(value = "JAVA", prefix = "class $X$ {", suffix = "}") @NotNull @NonNls String before,
    @Language(value = "JAVA", prefix = "class $X$ {", suffix = "}") @NotNull @NonNls String after) {
    doTest(hint, "class $X$ {" + before + "}", "class $X$ {\n    " + after + "\n}");
  }

  protected void doTest(String hint, @Language("JAVA") @NotNull @NonNls String before, @Language("JAVA") @NotNull @NonNls String after) {
    before = before.replace("/**/", "<caret>");
    myFixture.configureByText(JavaFileType.INSTANCE, before);
    final IntentionAction intention = findIntention(hint);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResult(after);
  }

  protected String getRelativePath() {
    assertNotNull(myRelativePath);
    return myRelativePath;
  }
}
