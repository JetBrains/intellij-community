package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.GroovyLoader;

/**
 * @author ven
 */
public class ResolvePropertyTest extends ResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/property/";
  }

  public void testParameter1() throws Exception {
    doTest("parameter1/A.groovy");
  }

  public void testClosureParameter1() throws Exception {
    doTest("closureParameter1/A.groovy");
  }

  public void testLocal1() throws Exception {
    doTest("local1/A.groovy");
  }

  public void testField1() throws Exception {
    doTest("field1/A.groovy");
  }

  public void testField2() throws Exception {
    doTest("field2/A.groovy");
  }

  public void testForVariable1() throws Exception {
    doTest("forVariable1/ForVariable.groovy");
  }

  public void testForVariable2() throws Exception {
    doTest("forVariable2/ForVariable.groovy");
  }

  public void testCatchParameter() throws Exception {
    doTest("CatchParameter/CatchParameter.groovy");
  }

  public void testField3() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("field3/A.groovy");
    GroovyResolveResult resolveResult = ref.advancedResolve();
    assertTrue(resolveResult.getElement() instanceof GrField);
    assertFalse(resolveResult.isValidResult());
  }

  public void testUndefinedVar1() throws Exception {
    PsiReference ref = configureByFile("undefinedVar1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrReferenceExpression);
    GrTopStatement statement = ((GroovyFile) resolved.getContainingFile()).getTopStatements()[2];
    assertTrue(resolved.equals(((GrAssignmentExpression) statement).getLValue()));
  }

  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy");
  }

  public void testDefinedVar1() throws Exception {
    doTest("definedVar1/A.groovy");
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrVariable);
  }

  private void doUndefinedVarTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrReferenceExpression);
  }

  protected void setUp() throws Exception {
    super.setUp();
    GroovyLoader.loadGroovy();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    assertNotNull(root);
    ContentEntry contentEntry = rootModel.addContentEntry(root);
    //rootModel.setJdk(JavaSdkImpl.getMockJdk15(""));
    contentEntry.addSourceFolder(root, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
  }
}