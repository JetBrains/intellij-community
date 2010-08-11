/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.resolve;


import com.intellij.psi.util.PropertyUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl
import org.jetbrains.plugins.groovy.util.TestUtils
import com.intellij.psi.*

/**
 * @author ven
 */
public class ResolveMethodTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "resolve/method/";
  }


  public void testStaticImport3() throws Exception {
    PsiReference ref = configureByFile("staticImport3/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals(((GrMethod) resolved).getParameters().length, 1);
    assertEquals(((GrMethod) resolved).getName(), "isShrimp");
  }

  public void testStaticImport() throws Exception {
    PsiReference ref = configureByFile("staticImport/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void _testImportStaticReverse() throws Throwable {
    PsiReference ref = configureByFile(getTestName(true) + "/" + getTestName(false) + ".groovy");
    assertNotNull(ref.resolve());
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
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("filterBase/A.groovy").getElement();
    assertNotNull(ref.resolve());
    assertEquals(1, ref.multiResolve(false).length);
  }

  public void testTwoCandidates() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("twoCandidates/A.groovy").getElement();
    assertNull(ref.resolve());
    assertEquals(2, ref.multiResolve(false).length);
  }

  public void testDefaultMethod1() throws Exception {
    PsiReference ref = configureByFile("defaultMethod1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testDefaultStaticMethod() throws Exception {
    PsiReference ref = configureByFile("defaultStaticMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
    assertTrue(((GrGdkMethodImpl) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testPrimitiveSubtyping() throws Exception {
    PsiReference ref = configureByFile("primitiveSubtyping/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
    assertTrue(((GrGdkMethodImpl) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testDefaultMethod2() throws Exception {
    PsiReference ref = configureByFile("defaultMethod2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testGrvy111() throws Exception {
    PsiReference ref = configureByFile("grvy111/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
    assertEquals(0, ((PsiMethod) resolved).getParameterList().getParametersCount());
    assertTrue(((PsiMethod) resolved).hasModifierProperty(PsiModifier.PUBLIC));
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
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testArrayDefault1() throws Exception {
    PsiReference ref = configureByFile("arrayDefault1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testSpreadOperator() throws Exception {
    PsiReference ref = configureByFile("spreadOperator/A.groovy");
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

  //test we don't resolve to field in case explicit getter is present
  public void testFromGetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
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
    PsiMethod resolved = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(resolved);
    assertTrue(resolved.isConstructor());
  }

  public void testConstructor1() throws Exception {
    PsiReference ref = configureByFile("constructor1/A.groovy");
    PsiMethod method = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(method);
    assertTrue(method.isConstructor());
    assertEquals(0, method.getParameterList().getParameters().length);
  }

  public void testConstructor2() throws Exception {
    PsiReference ref = configureByFile("constructor2/A.groovy");
    PsiMethod method = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNull(method);
  }

  //grvy-101
  public void testConstructor3() throws Exception {
    PsiReference ref = configureByFile("constructor3/A.groovy");
    PsiMethod method = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(method);
    assertTrue(method.isConstructor());
    assertEquals(0, method.getParameterList().getParameters().length);
  }

  public void testPartiallyDeclaredType() throws Exception {
    PsiReference ref = configureByFile("partiallyDeclaredType/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGeneric1() throws Exception {
    PsiReference ref = configureByFile("generic1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testNotAField() throws Exception {
    PsiReference ref = configureByFile("notAField/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testEscapedReferenceExpression() throws Exception {
    PsiReference ref = configureByFile("escapedReferenceExpression/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testListOfClasses() throws Exception {
    PsiReference ref = configureByFile("listOfClasses/A.groovy");
    PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testEmptyVsMap() throws Exception {
    PsiReference ref = configureByFile("emptyVsMap/A.groovy");
    PsiMethod resolved = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(resolved);
    assertEquals(0, resolved.getParameterList().getParametersCount());
  }

  public void testGrvy179() throws Exception {
    PsiReference ref = configureByFile("grvy179/A.groovy");
    assertNull(ref.resolve());
  }

  public void testPrivateScriptMethod() throws Exception {
    PsiReference ref = configureByFile("A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testAliasedConstructor() throws Exception {
    PsiReference ref = configureByFile("aliasedConstructor/A.groovy");
    PsiMethod resolved = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(resolved);
    assertEquals("JFrame", resolved.getName());
  }


  public void testFixedVsVarargs1() throws Exception {
    PsiReference ref = configureByFile("fixedVsVarargs1/A.groovy");
    PsiMethod resolved = ((GrNewExpression) ref.getElement().getParent()).resolveConstructor();
    assertNotNull(resolved);
    final GrParameter[] parameters = ((GrMethod) resolved).getParameters();
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].getType().getCanonicalText(), "int");
  }

  public void testFixedVsVarargs2() throws Exception {
    PsiReference ref = configureByFile("fixedVsVarargs2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiParameter[] parameters = ((PsiMethod) resolved).getParameterList().getParameters();
    assertEquals(parameters.length, 2);
    assertEquals(parameters[0].getType().getCanonicalText(), "java.lang.Class");
  }

  public void testReassigned1() throws Exception {
    PsiReference ref = configureByFile("reassigned1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    final GrParameter[] parameters = ((GrMethod) resolved).getParameters();
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].getType().getCanonicalText(), "java.lang.String");
  }

  public void testReassigned2() throws Exception {
    PsiReference ref = configureByFile("reassigned2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    final GrParameter[] parameters = ((GrMethod) resolved).getParameters();
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].getType().getCanonicalText(), "int");
  }

  public void testGenerics1() throws Exception {
    PsiReference ref = configureByFile("generics1/A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testGenericOverriding() throws Exception {
    PsiReference ref = configureByFile("genericOverriding/A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testUseOperator() throws Exception {
    PsiReference ref = configureByFile("useOperator/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod && ((PsiMethod) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testClosureMethodInsideClosure() throws Exception {
    PsiReference ref = configureByFile("closureMethodInsideClosure/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testScriptMethodInsideClosure() throws Exception {
    PsiReference ref = configureByFile("scriptMethodInsideClosure/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testExplicitGetter() throws Exception {
    PsiReference ref = configureByFile("explicitGetter/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertFalse(resolved instanceof GrAccessorMethod);

  }

  public void testGroovyAndJavaSamePackage() throws Exception {
    PsiReference ref = configureByFile("groovyAndJavaSamePackage/p/Ha.groovy");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testUnboxBigDecimal() throws Exception {
    PsiReference ref = configureByFile("unboxBigDecimal/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals(PsiType.DOUBLE, ((PsiMethod) resolved).getReturnType());
  }

  public void testGrvy1157() throws Exception {
    PsiReference ref = configureByFile("grvy1157/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1173() throws Exception {
    PsiReference ref = configureByFile("grvy1173/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1173_a() throws Exception {
    PsiReference ref = configureByFile("grvy1173_a/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1218() throws Exception {
    PsiReference ref = configureByFile("grvy1218/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodPointer1() throws Exception {
    PsiReference ref = configureByFile("A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodPointer2() throws Exception {
    PsiReference ref = configureByFile("methodPointer2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodCallTypeFromMultiResolve() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("methodCallTypeFromMultiResolve/A.groovy").getElement();
    assertNull(ref.resolve());
    assertTrue(((GrMethodCallExpression) ref.getParent()).getType().equalsToText("java.lang.String"));
  }

  public void testDefaultOverloaded() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("defaultOverloaded/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testDefaultOverloaded2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("defaultOverloaded2/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testDefaultOverloaded3() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("defaultOverloaded3/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment1() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("multipleAssignment1/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment2() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("multipleAssignment2/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment3() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("multipleAssignment3/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testClosureIntersect() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closureIntersect/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testClosureCallCurry() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("closureCallCurry/A.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testSuperFromGString() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("superFromGString/SuperFromGString.groovy").getElement();
    assertNotNull(ref.resolve());
  }

  public void testNominalTypeIsBetterThanNull() throws Exception {
    GrReferenceExpression ref = (GrReferenceExpression) configureByFile("nominalTypeIsBetterThanNull/A.groovy").getElement();
    final PsiType type = assertInstanceOf(ref.resolve(), GrMethod.class).getInferredReturnType();
    assertNotNull(type);
    assertTrue(type.equalsToText(CommonClassNames.JAVA_LANG_STRING));
  }
  
  public void testQualifiedSuperMethod() throws Exception {
    PsiReference ref = configureByFile("qualifiedSuperMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals("SuperClass", ((GrMethod) resolved).getContainingClass().getName());
  }

  public void testQualifiedThisMethod() throws Exception {
    PsiReference ref = configureByFile("qualifiedThisMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals("OuterClass", ((GrMethod) resolved).getContainingClass().getName());
  }

  public void testPrintMethodInAnonymousClass1() throws Exception {
    PsiReference ref = configureByFile("printMethodInAnonymousClass1/A.groovy");
    assertInstanceOf(ref.resolve(), GrGdkMethod.class);
  }

  public void testPrintMethodInAnonymousClass2() throws Exception {
    PsiReference ref = configureByFile("printMethodInAnonymousClass2/B.groovy");
    assertInstanceOf(ref.resolve(), GrGdkMethod.class);
  }

  public void testSubstituteWhenDisambiguating() throws Exception {
    myFixture.configureByText "a.groovy", """
class Zoo {
  def Object find(Object x) {}
  def <T> T find(Collection<T> c) {}

  {
    fin<caret>d(["a"])
  }

}"""
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertEquals 1, ((PsiMethod) ref.resolve()).typeParameters.length
  }

  public void testFooMethodInAnonymousClass() throws Exception {
    PsiReference ref = configureByFile("fooMethodInAnonymousClass/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
    assertEquals("A", ((PsiMethod)resolved).getContainingClass().getName());
  }

  public void testOptionalParameters1() throws Exception {
    PsiReference ref = configureByFile("optionalParameters1/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters2() throws Exception {
    PsiReference ref = configureByFile("optionalParameters2/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters3() throws Exception {
    PsiReference ref = configureByFile("optionalParameters3/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters4() throws Exception {
    PsiReference ref = configureByFile("optionalParameters4/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testNotInitializedVariable() throws Exception {
    PsiReference ref = configureByFile("notInitializedVariable/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testMethodVsField() throws Exception {
    final PsiReference ref = configureByFile("methodVsField/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, PsiMethod.class);
  }

  public void testLocalVariableVsGetter() throws Exception {
    final PsiReference ref = configureByFile("localVariableVsGetter/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, GrVariable.class);
  }

  public void testInvokeMethodViaThisInStaticContext() {
    final PsiReference ref = configureByFile("invokeMethodViaThisInStaticContext/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, PsiMethod.class);
    assertEquals "Class", element.getContainingClass().getName()
  }

  public void testInvokeMethodViaClassInStaticContext() {
    final PsiReference ref = configureByFile("invokeMethodViaClassInStaticContext/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, PsiMethod.class);
    assertEquals "Foo", element.getContainingClass().getName()
  }

  public void testUseInCategory() throws Exception {
    PsiReference ref = configureByFile("useInCategory/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  public void testMethodVsLocalVariable() {
    PsiReference ref = configureByFile("methodVsLocalVariable/A.groovy");
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrVariable
  }
}
