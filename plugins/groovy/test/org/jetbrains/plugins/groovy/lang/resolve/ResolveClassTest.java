package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.GroovyLoader;

/**
 * @author ven
 */
public class ResolveClassTest extends ResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/class/";
  }

  public void testSamePackage() throws Exception {
    PsiReference ref = configureByFile("samePackage/B.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrTypeDefinition);
  }
}
