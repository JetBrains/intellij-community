// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;
import org.junit.Assert;

public class GroovyResolveFileWithContextTest extends LightJavaCodeInsightFixtureTestCase {
  public void testResolve() {
    GroovyFile context = GroovyPsiElementFactory.getInstance(getProject())
      .createGroovyFile("class DummyClass {" + " String sss1 = null;" + "}", false, null);

    GroovyFile file = GroovyPsiElementFactory.getInstance(getProject()).createGroovyFile(
      """
        String sss2;
        def x = sss1 + sss2;
        """, false, null);

    if (file instanceof GroovyFileImpl groovyFile) {
      groovyFile.setContext(context.getLastChild());
    }

    checkResolved(file, "sss1");
    checkResolved(file, "sss2");
  }

  private static void checkResolved(PsiFile file, String referenceText) {
    int idx = file.getText().lastIndexOf(referenceText);
    Assert.assertTrue(idx > 0);
    PsiElement e = file.findElementAt(idx);

    while (e != null && !(e instanceof GrReferenceExpression)) {
      e = e.getParent();
    }


    Assert.assertNotNull(e);
    Assert.assertEquals(e.getTextLength(), referenceText.length());
    PsiReference reference = (PsiReference)e;
    Assert.assertNotNull(referenceText, reference.resolve());
  }
}
