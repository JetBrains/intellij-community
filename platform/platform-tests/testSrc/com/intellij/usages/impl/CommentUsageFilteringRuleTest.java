// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.CommentUsageFilteringRule;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.intellij.lang.annotations.Language;

public class CommentUsageFilteringRuleTest extends BasePlatformTestCase {
  public void testFiltersOutUsagesInJavadoc() {
    @Language("JAVA")
    String text = """
      class X {
        void foo() {}

        void bar() {
          foo();
        }

        /** see {@link #foo()} */
        void baz() {}
      }
      """;

    PsiFile file = myFixture.addFileToProject("X.java", text);

    Usage codeUsage = createUsageAt(file, file.getText().indexOf("foo();"));
    Usage javadocUsage = createUsageAt(file, file.getText().indexOf("#foo") + 1);

    assertTrue(CommentUsageFilteringRule.INSTANCE.isVisible(codeUsage, UsageTarget.EMPTY_ARRAY));
    assertFalse(CommentUsageFilteringRule.INSTANCE.isVisible(javadocUsage, UsageTarget.EMPTY_ARRAY));
  }

  private static Usage createUsageAt(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    assertNotNull(element);
    return new UsageInfo2UsageAdapter(new UsageInfo(element));
  }
}

