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

package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ven
 */
public class ResolveMethodTest extends GroovyResolveTestCase {
  final String basePath = TestUtils.testDataPath + "resolve/method/"

  public void testStaticImport3() {
    PsiReference ref = configureByFile("staticImport3/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 1);
    assertEquals(((GrMethod)resolved).name, "isShrimp");
  }

  public void testStaticImport() {
    PsiReference ref = configureByFile("staticImport/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void _testImportStaticReverse() {
    PsiReference ref = configureByFile(getTestName(true) + "/" + getTestName(false) + ".groovy");
    assertNotNull(ref.resolve());
  }


  public void testSimple() {
    PsiReference ref = configureByFile("simple/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 1);
  }

  public void testVarargs() {
    PsiReference ref = configureByFile("varargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 1);
  }

  public void testByName() {
    PsiReference ref = configureByFile("byName/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
  }

  public void testByName1() {
    PsiReference ref = configureByFile("byName1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 2);
  }

  public void testByNameVarargs() {
    PsiReference ref = configureByFile("byNameVarargs/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 1);
  }

  public void testParametersNumber() {
    PsiReference ref = configureByFile("parametersNumber/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(((GrMethod)resolved).parameters.length, 2);
  }

  public void testFilterBase() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("filterBase/A.groovy").element;
    assertNotNull(ref.resolve());
    assertEquals(1, ref.multiResolve(false).length);
  }

  public void testTwoCandidates() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("twoCandidates/A.groovy").element;
    assertNull(ref.resolve());
    assertEquals(2, ref.multiResolve(false).length);
  }

  public void testDefaultMethod1() {
    PsiReference ref = configureByFile("defaultMethod1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testDefaultStaticMethod() {
    PsiReference ref = configureByFile("defaultStaticMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
    assertTrue(((GrGdkMethodImpl) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testPrimitiveSubtyping() {
    PsiReference ref = configureByFile("primitiveSubtyping/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
    assertTrue(((GrGdkMethodImpl) resolved).hasModifierProperty(PsiModifier.STATIC));
  }

  public void testDefaultMethod2() {
    PsiReference ref = configureByFile("defaultMethod2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testGrvy111() {
    PsiReference ref = configureByFile("grvy111/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
    assertEquals(0, ((PsiMethod)resolved).parameterList.parametersCount);
    assertTrue(((PsiMethod) resolved).hasModifierProperty(PsiModifier.PUBLIC));
  }

  public void testScriptMethod() {
    PsiReference ref = configureByFile("scriptMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("groovy.lang.Script", ((PsiMethod)resolved).containingClass.qualifiedName);
  }

  public void testArrayDefault() {
    PsiReference ref = configureByFile("arrayDefault/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testArrayDefault1() {
    PsiReference ref = configureByFile("arrayDefault1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testSpreadOperator() {
    PsiReference ref = configureByFile("spreadOperator/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    GrMethodCallExpression methodCall = (GrMethodCallExpression)ref.element.parent;
    PsiType type = methodCall.type;
    assertTrue(type instanceof PsiClassType);
    PsiClass clazz = ((PsiClassType) type).resolve();
    assertNotNull(clazz);
    assertEquals("java.util.ArrayList", clazz.qualifiedName);
  }


  public void testSwingBuilderMethod() {
    PsiReference ref = configureByFile("swingBuilderMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertFalse(resolved.physical);
  }

  public void testSwingProperty() {
    PsiReference ref = configureByFile("swingProperty/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod) resolved));
    assertEquals("javax.swing.JComponent", ((PsiMethod)resolved).containingClass.qualifiedName);
  }

  public void testLangClass() {
    PsiReference ref = configureByFile("langClass/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.lang.Class", ((PsiMethod)resolved).containingClass.qualifiedName);
  }

  public void testComplexOverload() {
    PsiReference ref = configureByFile("complexOverload/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  //test we don't resolve to field in case explicit getter is present
  public void testFromGetter() {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testOverload1() {
    PsiReference ref = configureByFile("overload1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals("java.io.Serializable", ((PsiMethod)resolved).parameterList.parameters[0].type.canonicalText);
  }

  public void testConstructor() {
    PsiReference ref = configureByFile("constructor/A.groovy");
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(resolved);
    assertTrue(resolved.constructor);
  }

  public void testConstructor1() {
    PsiReference ref = configureByFile("constructor1/A.groovy");
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(method);
    assertTrue(method.constructor);
    assertEquals(0, method.parameterList.parameters.length);
  }

  public void testConstructor2() {
    PsiReference ref = configureByFile("constructor2/A.groovy");
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNull(method);
  }

  //grvy-101
  public void testConstructor3() {
    PsiReference ref = configureByFile("constructor3/A.groovy");
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(method);
    assertTrue(method.constructor);
    assertEquals(0, method.parameterList.parameters.length);
  }

  public void testWrongConstructor() {
    myFixture.addFileToProject('Classes.groovy', 'class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert !((GrNewExpression) ref.element.parent).advancedResolve().element
  }

  public void testLangImmutableConstructor() {
    myFixture.addClass("package groovy.lang; public @interface Immutable {}")
    myFixture.addFileToProject('Classes.groovy', '@Immutable class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testTransformImmutableConstructor() {
    myFixture.addClass("package groovy.transform; public @interface Immutable {}")
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.Immutable class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testTupleConstructor() {
    myFixture.addClass("package groovy.transform; public @interface TupleConstructor {}")
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.TupleConstructor class Foo { int a; final int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    def target = ((GrNewExpression) ref.element.parent).advancedResolve().element
    assert target instanceof PsiMethod
    assert target.parameterList.parametersCount == 2
    assert target.navigationElement instanceof PsiClass
  }

  public void testCanonicalConstructor() {
    myFixture.addClass("package groovy.transform; public @interface Canonical {}")
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.Canonical class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testInheritConstructors() {
    myFixture.addClass("package groovy.transform; public @interface InheritConstructors {}")
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.InheritConstructors class CustomException extends Exception {}')
    def ref = configureByText('new Cu<caret>stomException("msg")')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testPartiallyDeclaredType() {
    PsiReference ref = configureByFile("partiallyDeclaredType/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGeneric1() {
    PsiReference ref = configureByFile("generic1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testNotAField() {
    PsiReference ref = configureByFile("notAField/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testEscapedReferenceExpression() {
    PsiReference ref = configureByFile("escapedReferenceExpression/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testListOfClasses() {
    PsiReference ref = configureByFile("listOfClasses/A.groovy");
    PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testEmptyVsMap() {
    PsiReference ref = configureByFile("emptyVsMap/A.groovy");
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(resolved);
    assertEquals(0, resolved.parameterList.parametersCount);
  }

  public void testGrvy179() {
    PsiReference ref = configureByFile("grvy179/A.groovy");
    assertNull(ref.resolve());
  }

  public void testPrivateScriptMethod() {
    PsiReference ref = configureByFile("A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testAliasedConstructor() {
    PsiReference ref = configureByFile("aliasedConstructor/A.groovy");
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(resolved);
    assertEquals("JFrame", resolved.name);
  }


  public void testFixedVsVarargs1() {
    PsiReference ref = configureByFile("fixedVsVarargs1/A.groovy");
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod();
    assertNotNull(resolved);
    final GrParameter[] parameters = ((GrMethod)resolved).parameters;
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].type.canonicalText, "int");
  }

  public void testFixedVsVarargs2() {
    PsiReference ref = configureByFile("fixedVsVarargs2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    final PsiParameter[] parameters = ((PsiMethod)resolved).parameterList.parameters;
    assertEquals(parameters.length, 2);
    assertEquals(parameters[0].type.canonicalText, "java.lang.Class");
  }

  public void testReassigned1() {
    PsiReference ref = configureByFile("reassigned1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    final GrParameter[] parameters = ((GrMethod)resolved).parameters;
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].type.canonicalText, "java.lang.String");
  }

  public void testReassigned2() {
    PsiReference ref = configureByFile("reassigned2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    final GrParameter[] parameters = ((GrMethod)resolved).parameters;
    assertEquals(parameters.length, 1);
    assertEquals(parameters[0].type.canonicalText, "int");
  }

  public void testGenerics1() {
    PsiReference ref = configureByFile("generics1/A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testGenericOverriding() {
    PsiReference ref = configureByFile("genericOverriding/A.groovy");
    assertNotNull(ref.resolve());
  }

  public void testUseOperator() {
    PsiReference ref = configureByFile("useOperator/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrGdkMethod);
  }

  public void testClosureMethodInsideClosure() {
    PsiReference ref = configureByFile("closureMethodInsideClosure/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testScriptMethodInsideClosure() {
    PsiReference ref = configureByFile("scriptMethodInsideClosure/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testExplicitGetter() {
    PsiReference ref = configureByFile("explicitGetter/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertFalse(resolved instanceof GrAccessorMethod);

  }

  public void testGroovyAndJavaSamePackage() {
    PsiReference ref = configureByFile("groovyAndJavaSamePackage/p/Ha.groovy");
    assertTrue(ref.resolve() instanceof PsiMethod);
  }

  public void testUnboxBigDecimal() {
    myFixture.addClass("package java.math; public class BigDecimal {}");
    PsiReference ref = configureByFile("unboxBigDecimal/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals(PsiType.DOUBLE, ((PsiMethod)resolved).returnType);
  }

  public void testGrvy1157() {
    PsiReference ref = configureByFile("grvy1157/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1173() {
    PsiReference ref = configureByFile("grvy1173/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1173_a() {
    PsiReference ref = configureByFile("grvy1173_a/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testGrvy1218() {
    PsiReference ref = configureByFile("grvy1218/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodPointer1() {
    PsiReference ref = configureByFile("A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodPointer2() {
    PsiReference ref = configureByFile("methodPointer2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
  }

  public void testMethodCallTypeFromMultiResolve() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("methodCallTypeFromMultiResolve/A.groovy").element;
    assertNull(ref.resolve());
    assertTrue(((GrMethodCallExpression)ref.parent).type.equalsToText("java.lang.String"));
  }

  public void testDefaultOverloaded() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("defaultOverloaded/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testDefaultOverloaded2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("defaultOverloaded2/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testDefaultOverloaded3() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("defaultOverloaded3/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("multipleAssignment1/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("multipleAssignment2/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testMultipleAssignment3() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("multipleAssignment3/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testClosureIntersect() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closureIntersect/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testClosureCallCurry() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closureCallCurry/A.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testSuperFromGString() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("superFromGString/SuperFromGString.groovy").element;
    assertNotNull(ref.resolve());
  }

  public void testNominalTypeIsBetterThanNull() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("nominalTypeIsBetterThanNull/A.groovy").element;
    final PsiType type = assertInstanceOf(ref.resolve(), GrMethod.class).inferredReturnType;
    assertNotNull(type);
    assertTrue(type.equalsToText(CommonClassNames.JAVA_LANG_STRING));
  }
  
  public void testQualifiedSuperMethod() {
    PsiReference ref = configureByFile("qualifiedSuperMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals("SuperClass", ((GrMethod)resolved).containingClass.name);
  }

  public void testQualifiedThisMethod() {
    PsiReference ref = configureByFile("qualifiedThisMethod/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals("OuterClass", ((GrMethod)resolved).containingClass.name);
  }

  public void testPrintMethodInAnonymousClass1() {
    PsiReference ref = configureByFile("printMethodInAnonymousClass1/A.groovy");
    assertInstanceOf(ref.resolve(), GrGdkMethod.class);
  }

  public void testPrintMethodInAnonymousClass2() {
    PsiReference ref = configureByFile("printMethodInAnonymousClass2/B.groovy");
    assertInstanceOf(ref.resolve(), GrGdkMethod.class);
  }

  public void testSubstituteWhenDisambiguating() {
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

  public void testFooMethodInAnonymousClass() {
    PsiReference ref = configureByFile("fooMethodInAnonymousClass/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
    assertEquals("A", ((PsiMethod)resolved).containingClass.name);
  }

  public void testOptionalParameters1() {
    PsiReference ref = configureByFile("optionalParameters1/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters2() {
    PsiReference ref = configureByFile("optionalParameters2/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters3() {
    PsiReference ref = configureByFile("optionalParameters3/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testOptionalParameters4() {
    PsiReference ref = configureByFile("optionalParameters4/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testNotInitializedVariable() {
    PsiReference ref = configureByFile("notInitializedVariable/A.groovy");
    final PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiMethod.class);
  }

  public void testMethodVsField() {
    final PsiReference ref = configureByFile("methodVsField/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, PsiMethod.class);
  }

  public void testLocalVariableVsGetter() {
    final PsiReference ref = configureByFile("localVariableVsGetter/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, GrVariable.class);
  }

  public void testInvokeMethodViaThisInStaticContext() {
    final PsiReference ref = configureByFile("invokeMethodViaThisInStaticContext/A.groovy");
    final PsiElement element = ref.resolve();
    assertEquals "Class", assertInstanceOf(element, PsiMethod).containingClass.name
  }

  public void testInvokeMethodViaClassInStaticContext() {
    final PsiReference ref = configureByFile("invokeMethodViaClassInStaticContext/A.groovy");
    final PsiElement element = ref.resolve();
    assertInstanceOf(element, PsiMethod.class);
    assertEquals "Foo", assertInstanceOf(element, PsiMethod).containingClass.name
  }

  public void testUseInCategory() {
    PsiReference ref = configureByFile("useInCategory/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  public void testMethodVsLocalVariable() {
    PsiReference ref = configureByFile("methodVsLocalVariable/A.groovy");
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrVariable
  }

  public void testCommandExpressionStatement1() {
    PsiElement method = resolve("A.groovy")
    assertEquals "foo2", assertInstanceOf(method, GrMethod).name
  }

  public void testCommandExpressionStatement2() {
    PsiElement method = resolve("A.groovy")
    assertEquals "foo3", assertInstanceOf(method, GrMethod).name
  }

  public void testUpperCaseFieldAndGetter() {
    assertTrue resolve("A.groovy") instanceof GrMethod
  }

  public void testUpperCaseFieldWithoutGetter() {
    assertTrue resolve("A.groovy") instanceof GrAccessorMethod
  }

  public void testSpreadOperatorNotList() {
    assertInstanceOf resolve("A.groovy"), GrMethod
  }

  public void testMethodChosenCorrect() {
    final PsiElement resolved = resolve("A.groovy")
    assert "map" == assertInstanceOf(resolved, GrMethod).parameterList.parameters[0].name
  }

  public void testResolveCategories() {
    assertNotNull resolve("A.groovy")
  }

  public void testResolveValuesOnEnum() {
    assertNotNull resolve("A.groovy")
  }

  public void testAvoidResolveLockInClosure() {
    assertNotNull resolve("A.groovy")
  }

  public void resoleAsType() {
    assertInstanceOf resolve("A.groovy"), GrMethod
  }

  public void testPlusAssignment() {
    final PsiElement resolved = resolve("A.groovy")
    assertEquals("plus", assertInstanceOf(resolved, GrMethod).name)
  }

  public void testWrongGdkCallGenerics() {
    myFixture.configureByText("a.groovy",
                              "Map<File,String> map = [:]\n" +
                              "println map.ge<caret>t('', '')"
    );
    def ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertInstanceOf ref.resolve(), GrGdkMethod
  }

  public void testStaticImportInSamePackage() {
    myFixture.addFileToProject "pack/Foo.groovy", """package pack
class Foo {
  static def foo()
}"""
    PsiReference ref = configureByFile("staticImportInSamePackage/A.groovy", "A.groovy");
    assertNotNull(ref.resolve())
  }

  void testStringRefExpr1() {
    assertNotNull(resolve("a.groovy"));
  }

  void testStringRefExpr2() {
    assertNotNull(resolve("a.groovy"));
  }

  void testStringRefExpr3() {
    assertNotNull(resolve("a.groovy"));
  }

  void testNestedWith() {
    assertNotNull(resolve('a.groovy'))
  }

  void testCategory() {
    assertNotNull(resolve('a.groovy'))
  }

  void testAutoClone() {
    def element = resolve('a.groovy')
    assertInstanceOf element, PsiMethod
    assertTrue element.containingClass.name == 'Foo'
    assertSize 1, element.throwsList.referencedTypes
  }

  void testDontUseQualifierScopeInDGM() {
    assertNull resolve('a.groovy')
  }

  void testInferPlusType() {
    assertNotNull(resolve('a.groovy'))
  }

  void testCategoryClassMethod() {
    def resolved = resolve('a.groovy')
    assertInstanceOf(resolved, GrReflectedMethod)
    assertTrue(resolved.modifierList.hasModifierProperty(PsiModifier.STATIC))
  }

  void testMixinAndCategory() {
    def ref = configureByText("""
@Category(B)
class A {
  def foo() {print getName()}
}

@Mixin(A)
class B {
  def getName('B');
}

print new B().f<caret>oo()
""")

    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrGdkMethod)
    assertInstanceOf(resolved.staticMethod, GrReflectedMethod)
  }

  void testOnlyMixin() {
    def ref = configureByText("""
class A {
  def foo() {print getName()}
}

@Mixin(A)
class B {
  def getName('B');
}

print new B().f<caret>oo()
""")

    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrMethod)
    assertTrue(resolved.physical)
  }

  void testTwoMixinsInModifierList() {
    def ref = configureByText("""
class PersonHelper {
  def useThePerson() {
    Person person = new Person()

    person.getUsername()
    person.get<caret>Name()
  }
}

@Mixin(PersonMixin)
@Mixin(OtherPersonMixin)
class Person { }

class PersonMixin {
  String getUsername() { }
}

class OtherPersonMixin {
  String getName() { }
}
""")

    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrMethod)
    assertTrue(resolved.physical)
  }


  void testDisjunctionType() {
    def ref = configureByText ("""
import java.sql.SQLException
def test() {
        try {}
        catch (IOException | SQLException ex) {
            ex.prin<caret>tStackTrace();
        }
}""")
    assertNotNull(ref.resolve())
  }

  void testStringInjectionDontOverrideItParameter() {
    def ref = configureByText("""
[2, 3, 4].collect {"\${it.toBigDeci<caret>mal()}"}
""")
    assertNotNull(ref.resolve())
  }

  public void testPublicVsPrivateConstructor() {
    def resolved = (configureByText('throw new Assertion<caret>Error("foo")').element.parent as GrNewExpression).resolveMethod()
    assertNotNull resolved

    PsiParameter[] parameters = resolved.parameterList.parameters
    assertTrue parameters.length == 1
    assertEquals "java.lang.Object", parameters[0].type.canonicalText
  }

  public void testScriptMethodsInClass() {
    def ref = configureByText('''
class X {
  def foo() {
    scriptMetho<caret>d('1')
  }
}
def scriptMethod(String s){}
''')

    assertNull(ref.resolve())
  }

  public void testStaticallyImportedMethodsVsDGMMethods() {
    myFixture.addClass('''\
package p;
public class Matcher{}
''' )
    myFixture.addClass('''\
package p;
class Other {
  public static Matcher is(Matcher m){}
  public static Matcher create(){}
}''')

    def ref = configureByText('''\
import static p.Other.is
import static p.Other.create

i<caret>s(create())

''')

    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
    assertEquals 'Other', resolved.containingClass.name
  }

  public void testStaticallyImportedMethodsVsCurrentClassMethod() {
    myFixture.addClass('''\
package p;
class Other {
  public static Object is(Object m){}
}''')

    def ref = configureByText('''\
import static p.Other.is

class A {
  public boolean is(String o){true}

  public foo() {
    print i<caret>s('abc')
  }
}

''')

    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
    assertEquals 'Other', resolved.containingClass.name
  }

  public void testInapplicableStaticallyImportedMethodsVsCurrentClassMethod() {
    myFixture.addClass('''\
package p;
class Other {
  public static Object is(String m){}
}''')

    def ref = configureByText('''\
import static p.Other.is

class A {
  public boolean is(Object o){true}

  public foo() {
    print i<caret>s(new Object())
  }
}

''')

    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
    assertEquals 'A', resolved.containingClass.name
  }

  public void testInferArgumentTypeFromMethod1() {
    def ref = configureByText('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)

    a.subst<caret>ring(2)
}
''')
    assertNotNull(ref.resolve())
  }

  public void testInferArgumentTypeFromMethod2() {
    def ref = configureByText('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    a.subst<caret>ring(2)
  }
}
''')
    assertNotNull(ref.resolve())
  }

  public void testInferArgumentTypeFromMethod3() {
    def ref = configureByText('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)

    a.int<caret>Value()
}
''')
    assertNotNull(ref.resolve())
  }

  public void testInferArgumentTypeFromMethod4() {
    def ref = configureByText('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    a.intVal<caret>ue()
  }
}
''')
    assertNotNull(ref.resolve())
  }

  public void testStaticImportFromSuperClass() {
    def ref = configureByText('''\
import static Derived.fo<caret>o

class Base {
    static foo(){print 'foo'}
}

class Derived extends Base {
}

foo()
''')

    assertNotNull(ref.resolve())
  }

  public void testUsageOfStaticImportFromSuperClass() {
    def ref = configureByText('''\
import static Derived.foo

class Base {
    static foo(){print 'foo'}
}

class Derived extends Base {
}

fo<caret>o()
''')

    assertNotNull(ref.resolve())
  }

  public void testMixin() {
    def ref = configureByText('''\
@Mixin([Category1, Category2])
class A {
  def test1(){}
}


@Category(A)
class Category1 {
  boolean foo() {
    true

  }
}

@Category(A)
class Category2 {
  void bar() {
    fo<caret>o()
  }
}
''')
    assertNotNull(ref.resolve())
  }


  void testGroovyExtensions() {
    def ref = configureByText('pack._a.groovy', '''\
package pack

class StringExt {
  static sub(String s) {}
}

"".su<caret>b()''')

    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", """\
extensionClasses=\\
  pack.StringExt
""")

    assertNotNull(ref.resolve())
  }

  void testInitializerOfScriptField() {
    addGroovyTransformField()
    def ref = configureByText('''\
import groovy.transform.Field

def xx(){5}

@Field
def aa = 5 + x<caret>x()
''')
    assertInstanceOf(ref.resolve(), GrMethod)
  }

  void testStaticallyImportedProperty1() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def getFoo() {2}
  static def setFoo(def foo){}
}''')

    def ref = configureByText('''\
import static Foo.f<caret>oo
''')
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrMethod)
    assertEquals('getFoo', resolved.name)
  }

  void testStaticallyImportedProperty2() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def getFoo() {2}
  static def setFoo(def foo){}
}''')

    def ref = configureByText('''\
import static Foo.foo

setFo<caret>o(2)
''')
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrMethod)
    assertEquals('setFoo', resolved.name)
  }

}
