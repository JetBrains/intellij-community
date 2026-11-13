// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaWithGroovyCompletionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Test
  public void test_using_java_keywords_in_member_names() {
    runInEdtAndWait(() -> {
      groovy.get().addFileToProject("a.groovy", """
        class Foo {
          static void "const"() {}
          static final int "continue" = 2;
        }
        """);
      groovy.get().configureByText("a.java", "class Bar {{ con<caret> }}");
      groovy.get().complete(CompletionType.BASIC, 2);
      assertFalse(groovy.get().getLookupElementStrings().contains("const"));
      assertFalse(groovy.get().getLookupElementStrings().contains("continue"));
    });
  }

  @Test
  public void test_using_java_expression_keywords_in_member_names() {
    runInEdtAndWait(() -> {
      groovy.get().addFileToProject("a.groovy", """
        class Foo {
          static void "this"() {}
        }
        """);
      groovy.get().configureByText("a.java", "class Bar {{ this<caret> }}");
      groovy.get().complete(CompletionType.BASIC, 2);
      LookupElement[] elements = groovy.get().getLookupElements();
      assertTrue(elements == null || ContainerUtil.find(elements,
                                                        element -> element.getLookupString().equals("this") &&
                                                                   element.getObject() instanceof PsiMethod) == null);
    });
  }
}
