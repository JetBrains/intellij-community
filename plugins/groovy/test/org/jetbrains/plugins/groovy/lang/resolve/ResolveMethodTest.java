/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
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
    PsiReference ref = configureByFile("simple/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testVarargs() throws Exception {
    PsiReference ref = configureByFile("varargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testByName() throws Exception {
    PsiReference ref = configureByFile("byName/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
  }

  public void testByName1() throws Exception {
    PsiReference ref = configureByFile("byName1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testByNameVarargs() throws Exception {
    PsiReference ref = configureByFile("byNameVarargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
  }

  public void testParametersNumber() throws Exception {
    PsiReference ref = configureByFile("parametersNumber/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 2);
  }

  public void testFilterBase() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("filterBase/A.groovy");
    assertNotNull(ref.resolve());
    assertEquals(1, ref.multiResolve(false).length);
  }

  public void testTwoCandidates() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("twoCandidates/A.groovy");
    assertNull(ref.resolve());
    assertEquals(2, ref.multiResolve(false).length);
  }

  public void testDefaultMethod1() throws Exception {
    PsiReference ref = configureByFile("defaultMethod1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testDefaultStaticMethod() throws Exception {
    PsiReference ref = configureByFile("defaultStaticMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
    assertTrue(((DefaultGroovyMethod) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testPrimitiveSubtyping() throws Exception {
    PsiReference ref = configureByFile("primitiveSubtyping/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof DefaultGroovyMethod);
    assertTrue(((DefaultGroovyMethod) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testDefaultMethod2() throws Exception {
    PsiReference ref = configureByFile("defaultMethod2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testScriptMethod() throws Exception {
   PsiReference ref = configureByFile("scriptMethod/A.groovy");
   PsiElement resolved = ref.resolve();
   assertTrue(resolved instanceof PsiMethod);
   assertEquals("groovy.lang.Script", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
 }

  public void testArrayDefault() throws Exception {
    PsiReference ref = configureByFile("arrayDefault/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testArrayDefault1() throws Exception {
    PsiReference ref = configureByFile("arrayDefault1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof DefaultGroovyMethod);
  }

  public void testSpreadOperator() throws Exception {
    PsiReference ref = configureByFile("spreadOperator/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    GrMethodCall methodCall = (GrMethodCall) ref.getElement().getParent();
    PsiType type = methodCall.getType();
    assertTrue(type instanceof PsiClassType);
    PsiClass clazz = ((PsiClassType) type).resolve();
    assertNotNull(clazz);
    assertEquals("java.util.List", clazz.getQualifiedName());
  }


  public void testSwingBuilderMethod() throws Exception {
    PsiReference ref = configureByFile("swingBuilderMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertFalse(resolved.isPhysical());
  }

  public void testSwingProperty() throws Exception {
    PsiReference ref = configureByFile("swingProperty/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod) resolved));
    assertEquals("javax.swing.JComponent", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
  }

  public void testLangClass() throws Exception {
    PsiReference ref = configureByFile("langClass/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.lang.Class", ((PsiMethod) resolved).getContainingClass().getQualifiedName());
  }

  public void testComplexOverload() throws Exception {
    PsiReference ref = configureByFile("complexOverload/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testOverload1() throws Exception {
    PsiReference ref = configureByFile("overload1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.io.Serializable", ((PsiMethod) resolved).getParameterList().getParameters()[0].getType().getCanonicalText());
  }

  public void testConstructor() throws Exception {
    PsiReference ref = configureByFile("constructor/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(((PsiMethod) resolved).isConstructor());
  }

  public void testConstructor1() throws Exception {
    PsiReference ref = configureByFile("constructor1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) resolved;
    assertTrue(method.isConstructor());
    assertEquals(0, method.getParameterList().getParameters().length);
  }

  public void testConstructor2() throws Exception {
    PsiReference ref = configureByFile("constructor2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) resolved;
    assertTrue(method.isConstructor());
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    assertEquals(0, parameters.length);
    assertTrue(parameters[0].getType().equalsToText("java.util.Map"));
  }

}
