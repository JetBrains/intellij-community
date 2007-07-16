/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultGroovyMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolveMethodTest extends GroovyResolveTestCase {

  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/method/";
  }

  public void testSimple() throws Exception {
    PsiReference ref = configureByFile("simple/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testVarargs() throws Exception {
    PsiReference ref = configureByFile("varargs/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testByName() throws Exception {
    PsiReference ref = configureByFile("byName/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
  }

  public void testByName1() throws Exception {
    PsiReference ref = configureByFile("byName1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testByNameVarargs() throws Exception {
    PsiReference ref = configureByFile("byNameVarargs/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testParametersNumber() throws Exception {
    PsiReference ref = configureByFile("parametersNumber/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testFilterBase() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("filterBase/ABCF.groovy");
    assertNotNull(ref.resolve());
    assertEquals(1, ref.multiResolve(false).length);
  }

  public void testTwoCandidates() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("twoCandidates/ABCF.groovy");
    assertNull(ref.resolve());
    assertEquals(2, ref.multiResolve(false).length);
  }

  public void testDefaultMethod1() throws Exception {
    PsiReference ref = configureByFile("defaultMethod1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testDefaultStaticMethod() throws Exception {
    PsiReference ref = configureByFile("defaultStaticMethod/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
    assertTrue(((DefaultGroovyMethod) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testPrimitiveSubtyping() throws Exception {
    PsiReference ref = configureByFile("primitiveSubtyping/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
    assertTrue(((DefaultGroovyMethod) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testDefaultMethod2() throws Exception {
    PsiReference ref = configureByFile("defaultMethod2/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testGrvy111() throws Exception {
    PsiReference ref = configureByFile("grvy111/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
    assertEquals(0, ((PsiMethod) resolved).getParameterList().getParametersCount());
    assertTrue(((PsiMethod) resolved).hasModifierProperty(PsiModifier.PUBLIC));
  }

  public void testScriptMethod() throws Exception {
   PsiReference ref = configureByFile("scriptMethod/ABCF.groovy");
   PsiElement resolved = ref.resolve();
   assertTrue(resolved instanceof PsiMethod);
   assertEquals("groovy.lang.Script", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
 }

  public void testArrayDefault() throws Exception {
    PsiReference ref = configureByFile("arrayDefault/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testArrayDefault1() throws Exception {
    PsiReference ref = configureByFile("arrayDefault1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testSpreadOperator() throws Exception {
    PsiReference ref = configureByFile("spreadOperator/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    GrMethodCallExpression methodCall = (GrMethodCallExpression) ref.getElement().getParent();
    PsiType type = methodCall.getType();
    assertTrue(type instanceof PsiClassType);
    PsiClass clazz = ((PsiClassType) type).resolve();
    assertNotNull(clazz);
    assertEquals("java.util.List", clazz.getQualifiedName());
  }


  public void testSwingBuilderMethod() throws Exception {
    PsiReference ref = configureByFile("swingBuilderMethod/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertFalse(resolved.isPhysical());
  }

  public void testSwingProperty() throws Exception {
    PsiReference ref = configureByFile("swingProperty/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod) resolved));
    assertEquals("javax.swing.JComponent", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
  }

  public void testLangClass() throws Exception {
    PsiReference ref = configureByFile("langClass/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.lang.Class", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
  }

  public void testComplexOverload() throws Exception {
    PsiReference ref = configureByFile("complexOverload/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  //test we don't resolve to field in case explicit getter is present
  public void testFromGetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testOverload1() throws Exception {
    PsiReference ref = configureByFile("overload1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.io.Serializable", ((PsiMethod) resolved).getParameterList().getParameters()[0].getType().getCanonicalText());
  }

  public void testConstructor() throws Exception {
    PsiReference ref = configureByFile("constructor/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(((PsiMethod) resolved).isConstructor());
  }

  public void testConstructor1() throws Exception {
    PsiReference ref = configureByFile("constructor1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) resolved;
    assertTrue(method.isConstructor());
    assertEquals(0, method.getParameterList().getParameters().length);
  }

  public void testConstructor2() throws Exception {
    PsiReference ref = configureByFile("constructor2/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) resolved;
    assertTrue(method.isConstructor());
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters[0].getType().equalsToText("java.util.Map"));
  }

  //grvy-101
  public void testConstructor3() throws Exception {
    PsiReference ref = configureByFile("constructor3/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) resolved;
    assertTrue(method.isConstructor());
    assertEquals(0, method.getParameterList().getParameters().length);
  }

  public void testStaticImport() throws Exception {
    PsiReference ref = configureByFile("staticImport/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testPartiallyDeclaredType() throws Exception {
    PsiReference ref = configureByFile("partiallyDeclaredType/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGeneric1() throws Exception {
    PsiReference ref = configureByFile("generic1/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testNotAField() throws Exception {
    PsiReference ref = configureByFile("notAField/ABCF.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }
}
