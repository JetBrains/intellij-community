// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyResolveFileWithContextTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testResolve() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      GroovyFile context = GroovyPsiElementFactory.getInstance(myFixture.getProject())
        .createGroovyFile("class DummyClass {" + " String sss1 = null;" + "}", false, null);

      GroovyFile file = GroovyPsiElementFactory.getInstance(myFixture.getProject()).createGroovyFile(
        """
          String sss2;
          def x = sss1 + sss2;
          """, false, null);

      if (file instanceof GroovyFileImpl groovyFile) {
        groovyFile.setContext(context.getLastChild());
      }

      checkResolved(file, "sss1");
      checkResolved(file, "sss2");
    });
  }

  private static void checkResolved(PsiFile file, String referenceText) {
    int idx = file.getText().lastIndexOf(referenceText);
    assertTrue(idx > 0);
    PsiElement e = file.findElementAt(idx);

    while (e != null && !(e instanceof GrReferenceExpression)) {
      e = e.getParent();
    }

    assertNotNull(e);
    assertEquals(e.getTextLength(), referenceText.length());
    PsiReference reference = (PsiReference)e;
    assertNotNull(reference.resolve(), referenceText);
  }
}
