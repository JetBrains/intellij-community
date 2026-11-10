// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GrClassUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testFindClassByName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
            public class InnerClasses {
              static class Bar { }
              static class Bar$ {
                static class $Foo { }
              }
            }\
      """.stripIndent());

    ReadAction.runBlocking(() -> {
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "InnerClasses$Bar"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "InnerClasses$Bar$"));
      assertNotNull(ClassUtil.findPsiClassByJVMName(PsiManager.getInstance(myFixture.getProject()), "InnerClasses$Bar$$$Foo"));
    });
  }
}
