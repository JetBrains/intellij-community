package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolvePropertyTest extends GroovyResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/property/";
  }

  public void testParameter1() throws Exception {
    doTest("parameter1/ABCF.groovy");
  }

  public void testClosureParameter1() throws Exception {
    doTest("closureParameter1/ABCF.groovy");
  }

  public void testLocal1() throws Exception {
    doTest("local1/ABCF.groovy");
  }

  public void testField1() throws Exception {
    doTest("field1/ABCF.groovy");
  }

  public void testField2() throws Exception {
    doTest("field2/ABCF.groovy");
  }

  public void testForVariable1() throws Exception {
    doTest("forVariable1/ForVariable.groovy");
  }

  public void testArrayLength() throws Exception {
    doTest("arrayLength/ABCF.groovy");
  }

  public void testFromGetter() throws Exception {
    doTest("fromGetter/ABCF.groovy");
  }

  public void testFromSetter() throws Exception {
    doTest("fromSetter/ABCF.groovy");
  }

  public void testForVariable2() throws Exception {
    doTest("forVariable2/ForVariable.groovy");
  }

  public void testCatchParameter() throws Exception {
    doTest("CatchParameter/CatchParameter.groovy");
  }

  public void testCaseClause() throws Exception {
    doTest("caseClause/CaseClause.groovy");
  }

  public void testGrvy104() throws Exception {
    doTest("grvy104/Test.groovy");
  }

  public void testField3() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("field3/ABCF.groovy");
    GroovyResolveResult resolveResult = ref.advancedResolve();
    assertTrue(resolveResult.getElement() instanceof GrField);
    assertFalse(resolveResult.isValidResult());
  }

  public void testToGetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("toGetter/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod) resolved));
  }

  public void testToSetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("toSetter/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod) resolved));
  }

  public void testUndefinedVar1() throws Exception {
    PsiReference ref = configureByFile("undefinedVar1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrReferenceExpression);
    GrTopStatement statement = ((GroovyFile) resolved.getContainingFile()).getTopStatements()[2];
    assertTrue(resolved.equals(((GrAssignmentExpression) statement).getLValue()));
  }

  public void testRecursive1() throws Exception {
    PsiReference ref = configureByFile("recursive1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrField);
  }

  public void testRecursive2() throws Exception {
    PsiReference ref = configureByFile("recursive2/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertNull(((GrMethod) resolved).getReturnType());
  }

  public void testNotAField() throws Exception {
    PsiReference ref = configureByFile("notAField/ABCF.groovy");
    assertNull(ref.resolve());
  }

  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/ABCF.groovy");
  }

  public void testDefinedVar1() throws Exception {
    doTest("definedVar1/ABCF.groovy");
  }

  public void testOperatorOverload() throws Exception {
    doTest("operatorOverload/ABCF.groovy");
  }

  public void testStackOverflow() throws Exception {
    doTest("stackOverflow/ABCF.groovy");
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
}