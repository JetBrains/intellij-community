package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.util.TestUtils;

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
    assertNotNull(resolveResult);
    final PsiElement element = resolveResult.getElement();
    assertNotNull(element);
    assertEquals(staticOk, resolveResult.isStaticsOK());
  }

  public void testPropInStaticInnerClass() {
    doTest(false);
  }

  public void testThisInStaticInnerClass() {
    doTest(true);
  }
}
