// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public final class GroovyCompletion60Test {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @Test
  public void testNoDuplicates() {
    groovy.get().configureByText("A.groovy", """
      class Existence {
      
        static void main(String[] args) {
          java.util.List<String> l = of<caret>
        }
      }
      """);

    groovy.get().complete(CompletionType.SMART, 2);
    LookupElement[] elements = groovy.get().getLookupElements();
    ReadAction.run(() -> assertNotSame(elements[0].getPsiElement(), elements[1].getPsiElement()));
  }


}
