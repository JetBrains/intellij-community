package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolveClassTest extends GroovyResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/class/";
  }

  public void testSamePackage() throws Exception {
    doTest("samePackage/B.groovy");
  }

  public void testImplicitImport() throws Exception {
    doTest("implicitImport/B.groovy");
  }

  public void testOnDemandImport() throws Exception {
    doTest("onDemandImport/B.groovy");
  }

  public void testSimpleImport() throws Exception {
    doTest("simpleImport/B.groovy");
  }

  public void testQualifiedName() throws Exception {
    doTest("qualifiedName/B.groovy");
  }

  public void testImportAlias() throws Exception {
    doTest("qualifiedName/B.groovy");
  }

  public void testQualifiedRefExpr() throws Exception {
    doTest("qualifiedRefExpr/A.groovy");
  }

  public void testGrvy102() throws Exception {
    doTest("grvy102/Test.groovy");
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiClass);
  }
}
