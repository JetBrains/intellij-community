// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.completion
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

abstract class GroovyCompletionTestBase extends LightCodeInsightFixtureTestCase {

  protected void doSmartTest() {
    doCompletionTest(CompletionType.SMART)
  }

  protected void doBasicTest(String before =  null, String after = null) {
    doCompletionTest(before, after, CompletionType.BASIC)
  }

  protected void doSmartTest(String before, String after) {
    doCompletionTest(before, after, CompletionType.SMART)
  }

  protected void checkResult() {
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true)
  }

  protected void doCompletionTest(String before = null, String after = null, String type = "", CompletionType ct) {
    if (before == null) {
      myFixture.configureByFile(getTestName(false) + ".groovy")
    }
    else {
      myFixture.configureByText(getTestName(false) + ".groovy", before)
    }

    myFixture.complete(ct)
    type.each { myFixture.type(it) }

    assertNull(myFixture.lookupElementStrings as String, myFixture.lookupElements)
    if (after == null) {
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy", true)
    }
    else {
      myFixture.checkResult(after, true)
    }
  }

  protected void doVariantableTest(String before = null, String type = "", CompletionType ct, CompletionResult testType = CompletionResult.equal, int completionCount = 1, String... variants) {
    if (before == null) {
      myFixture.configureByFile(getTestName(false) + ".groovy")
    }
    else {
      myFixture.configureByText(getTestName(false) + ".groovy", before)
    }

    myFixture.complete(ct, completionCount)
    type.each { myFixture.type(it) }

    assertNotNull(myFixture.lookupElements)

    final actual = myFixture.lookupElementStrings
    switch (testType) {
      case CompletionResult.contain:
        assertTrue(myFixture.lookupElementStrings as String, actual.containsAll(variants))
        break
      case CompletionResult.equal:
        if (!variants) {
          assert !actual
        } else {
          myFixture.assertPreferredCompletionItems(0, variants)
        }
        break
      case CompletionResult.notContain:
        variants.each {
          assertFalse(myFixture.lookupElementStrings as String, actual.contains(it))
        }
    }
  }


  void doVariantableTest(String... variants) {
    doVariantableTest(CompletionType.BASIC, variants)
  }

  void doHasVariantsTest(String... variants) {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, variants)
  }

  void doSmartCompletion(String... variants) {
    doVariantableTest(CompletionType.SMART, variants)
  }

  void checkCompletion(String before, String type, String after) {
    doCompletionTest(before, after, type, CompletionType.BASIC)
  }

  void checkSingleItemCompletion(String before, String after) {
    doCompletionTest(before, after, CompletionType.BASIC)
  }

  void doNoVariantsTest(String before, String... excludedVariants) {
    doVariantableTest(before, "", CompletionType.BASIC, CompletionResult.notContain, excludedVariants)
  }

  protected static def caseSensitiveNone() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
  }

  LookupImpl getLookup() {
    LookupManager.getActiveLookup(myFixture.editor)
  }

  void addCompileStatic() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic{
}
''')
  }
}
