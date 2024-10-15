// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;

public class JavaWithGroovyCompletionTest extends GroovyCompletionTestBase {
  public void test_using_java_keywords_in_member_names() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static void "const"() {}
        static final int "continue" = 2;
      }
      """);
    myFixture.configureByText("a.java", "class Bar {{ con<caret> }}");
    myFixture.complete(CompletionType.BASIC, 2);
    assert !(myFixture.getLookupElementStrings().contains("const"));
    assert !(myFixture.getLookupElementStrings().contains("continue"));
  }

  public void test_using_java_expression_keywords_in_member_names() {
    myFixture.addFileToProject("a.groovy", """
      class Foo {
        static void "this"() {}
      }
      """);
    myFixture.configureByText("a.java", "class Bar {{ this<caret> }}");
    myFixture.complete(CompletionType.BASIC, 2);
    LookupElement[] elements = myFixture.getLookupElements();
    assertTrue(elements == null || ContainerUtil.find(elements,
                                                      element -> element.getLookupString().equals("this") &&
                                                                 element.getObject() instanceof PsiMethod) == null);
  }
}
