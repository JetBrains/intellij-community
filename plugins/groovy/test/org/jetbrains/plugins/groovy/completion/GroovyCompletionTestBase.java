// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.Arrays;
import java.util.List;

public abstract class GroovyCompletionTestBase extends LightJavaCodeInsightFixtureTestCase {
  protected void doSmartTest() {
    doCompletionTest(CompletionType.SMART);
  }

  protected void doBasicTest(String before, String after) {
    doCompletionTest(before, after, CompletionType.BASIC);
  }

  protected void doBasicTest(String before) {
    doBasicTest(before, null);
  }

  protected void doBasicTest() {
    doBasicTest(null, null);
  }

  protected void doSmartTest(String before, String after) {
    doCompletionTest(before, after, CompletionType.SMART);
  }

  protected void checkResult() {
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
  }

  protected void doCompletionTest(String before, String after, String type, CompletionType ct) {
    if (before == null) {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }
    else {
      myFixture.configureByText(getTestName(false) + ".groovy", before);
    }


    myFixture.complete(ct);
    for (char ch : type.toCharArray()) {
      myFixture.type(ch);
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    String msg = "<empty>";
    if (myFixture.getLookupElementStrings() != null) {
      msg = String.join(", ", myFixture.getLookupElementStrings());
    }
    assertNull(msg, myFixture.getLookupElements());
    if (after == null) {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true);
    }
    else {
      myFixture.checkResult(after, true);
    }
  }

  protected void doCompletionTest(String before, String after, CompletionType ct) {
    doCompletionTest(before, after, "", ct);
  }

  protected void doCompletionTest(String before, CompletionType ct) {
    doCompletionTest(before, null, "", ct);
  }

  protected void doCompletionTest(CompletionType ct) {
    doCompletionTest(null, null, "", ct);
  }

  protected void doVariantableTest(String before,
                                   String type,
                                   CompletionType ct,
                                   CompletionResult testType,
                                   int completionCount,
                                   String... variants) {
    if (before == null) {
      myFixture.configureByFile(getTestName(false) + ".groovy");
    }
    else {
      myFixture.configureByText(getTestName(false) + ".groovy", before);
    }

    myFixture.complete(ct, completionCount);
    for (char ch : type.toCharArray()) {
      myFixture.type(ch);
    }
    assertNotNull(myFixture.getLookupElements());

    final List<String> actual = myFixture.getLookupElementStrings();
    String msg = "<empty>";
    if (myFixture.getLookupElementStrings() != null) {
      msg = String.join(", ", myFixture.getLookupElementStrings());
    }
    switch (testType) {
      case contain:
        assertTrue(msg, actual.containsAll(Arrays.asList(variants)));
        break;
      case equal:
        if (variants.length == 0) {
          assertTrue(actual.isEmpty());
        }
        else {
          myFixture.assertPreferredCompletionItems(0, variants);
        }
        break;
      case notContain:
        for (String variant : variants) {
          assertFalse(msg, actual.contains(variant));
        }
        break;
    }
  }

  protected void doVariantableTest(String before, String type, CompletionType ct, CompletionResult testType, String... variants) {
    doVariantableTest(before, type, ct, testType, 1, variants);
  }

  protected void doVariantableTest(String before, String type, CompletionType ct, String... variants) {
    doVariantableTest(before, type, ct, CompletionResult.equal, 1, variants);
  }

  protected void doVariantableTest(String before, CompletionType ct, String... variants) {
    doVariantableTest(before, "", ct, CompletionResult.equal, 1, variants);
  }

  protected void doVariantableTest(CompletionType ct, String... variants) {
    doVariantableTest(null, "", ct, CompletionResult.equal, 1, variants);
  }

  public void doVariantableTest(String... variants) {
    doVariantableTest(CompletionType.BASIC, variants);
  }

  public void doHasVariantsTest(String... variants) {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, variants);
  }

  public void doSmartCompletion(String... variants) {
    doVariantableTest(CompletionType.SMART, variants);
  }

  public void checkCompletion(String before, String type, String after) {
    doCompletionTest(before, after, type, CompletionType.BASIC);
  }

  public void checkSingleItemCompletion(String before, String after) {
    doCompletionTest(before, after, CompletionType.BASIC);
  }

  public void doNoVariantsTest(String before, String... excludedVariants) {
    doVariantableTest(before, "", CompletionType.BASIC, CompletionResult.notContain, excludedVariants);
  }

  protected static int caseSensitiveNone() {
    return CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
  }

  public LookupImpl getLookup() {
    return ((LookupImpl)(LookupManager.getActiveLookup(myFixture.getEditor())));
  }

  public void addCompileStatic() {
    myFixture.addClass("""
                         package groovy.transform;
                         public @interface CompileStatic{
                         }
                         """);
  }
}
