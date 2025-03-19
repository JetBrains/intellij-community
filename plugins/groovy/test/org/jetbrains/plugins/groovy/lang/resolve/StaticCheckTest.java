// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.Assert;

/**
 * @author Medvedev Max
 */
public class StaticCheckTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/static/";
  }

  protected void doTest(boolean staticOk) {
    final GroovyResolveResult resolveResult = advancedResolve("a.groovy");
    Assert.assertNotNull(resolveResult);
    final PsiElement element = resolveResult.getElement();
    Assert.assertNotNull(element);
    Assert.assertEquals(staticOk, resolveResult.isStaticsOK());
  }

  public void testPropInStaticInnerClass() {
    doTest(false);
  }

  public void testThisInStaticInnerClass() {
    doTest(true);
  }

  public void testWithInsideTheClass() {
    doTest(true);
  }
}
