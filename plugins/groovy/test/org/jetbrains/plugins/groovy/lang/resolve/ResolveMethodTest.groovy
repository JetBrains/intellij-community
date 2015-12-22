/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.util.NotNullCachedComputableWrapper
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
    addImmutable()
    myFixture.addFileToProject('Classes.groovy', '@Immutable class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testTransformImmutableConstructor() {
    addImmutable()
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.Immutable class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testTupleConstructor() {
    addTupleConstructor()
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.TupleConstructor class Foo { int a; final int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    def target = ((GrNewExpression) ref.element.parent).advancedResolve().element
    assert target instanceof PsiMethod
    assert target.parameterList.parametersCount == 2
    assert target.navigationElement instanceof PsiClass
  }

  public void testCanonicalConstructor() {
    addCanonical()
    myFixture.addFileToProject('Classes.groovy', '@groovy.transform.Canonical class Foo { int a; int b }')
    def ref = configureByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression) ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  public void testInheritConstructors() {
    addInheritConstructor()
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
    def element = resolve('a.groovy', PsiMethod)
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
    def resolved = resolve('a.groovy', GrReflectedMethod)
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
    assertInstanceOf(resolved, PsiMethod)
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
    assertInstanceOf(resolved, PsiMethod)
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

    def resolved = assertInstanceOf(ref.resolve(), PsiMethod)
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

    def resolved = assertInstanceOf(ref.resolve(), PsiMethod)
    assertEquals 'A', resolved.containingClass.name
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

    def resolved = assertInstanceOf(ref.resolve(), PsiMethod)
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

  void testRuntimeMixin1() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

metaClass.mixin(Foo)
d<caret>oSmth()
''', PsiMethod)
  }

  void testRuntimeMixin2() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

this.metaClass.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin3() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.metaClass.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin4() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.class.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin5() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin6() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    metaClass.mixin(Foo)
    d<caret>oSmth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin7() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    this.metaClass.mixin(Foo)
    do<caret>Smth()
  }
}

''', PsiMethod)
  }

  void testRuntimeMixin8() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.metaClass.mixin(Foo)
    do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin9() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.class.mixin(Foo)
    do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin10() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.mixin(Foo)
    do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin11() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

metaClass.mixin(Foo)
new _a().d<caret>oSmth()
''', PsiMethod)
  }

  void testRuntimeMixin12() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

this.metaClass.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin13() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.metaClass.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin14() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.class.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin15() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_a.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  void testRuntimeMixin16() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    metaClass.mixin(Foo)
    new _a().d<caret>oSmth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin17() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    this.metaClass.mixin(Foo)
    new _a().do<caret>Smth()
  }
}

''', PsiMethod)
  }

  void testRuntimeMixin18() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.metaClass.mixin(Foo)
    new _a().do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin19() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.class.mixin(Foo)
    new _a().do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin20() {
    resolveByText('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.mixin(Foo)
    new _a().do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin21() {
    resolveByText('''\
class Foo {
    public void doSmth() {
        println "hello"
    }
}

class _a {
  def foo() {
    _a.mixin(Foo)
    new _a().do<caret>Smth()
  }
}
''', PsiMethod)
  }

  void testRuntimeMixin22() {
    assertNull resolveByText('''\
class ReentrantLock {}

ReentrantLock.metaClass.withLock = { nestedCode -> }

new ReentrantLock().withLock {
    fo<caret>o(3)
}
''')
  }

  void testRuntimeMixin23() {
    assertNotNull resolveByText('''\
class ReentrantLock {}

ReentrantLock.metaClass.withLock = { nestedCode -> }

new ReentrantLock().withLock {
    withL<caret>ock(2)
}
''')
  }

  void testRunnableVsCallable() {
    final PsiMethod method = resolveByText('''\
import java.util.concurrent.Callable


void bar(Runnable c) {}

void bar(Callable<?> c) {}

b<caret>ar {
    print 2
}

''', PsiMethod)

    assertTrue(method.parameterList.parameters[0].type.equalsToText('java.lang.Runnable'))
  }

  void testOneArgVsEllipsis1() {
    def method = resolveByText('''\
class X {
    void foo(Object... args) {
        print 'many'
    }

    void foo(Object arg) {
        print 'one'
    }

    void foo() {
        print 'none'
    }
}

new X().fo<caret>o('abc')''', PsiMethod)

    assertFalse(method.parameterList.parameters[0].type instanceof PsiEllipsisType)
  }

  void testOneArgVsEllipsis2() {
    def method = resolveByText('''\
class X {
    void foo(Object arg) {
        print 'one'
    }

    void foo(Object... args) {
        print 'many'
    }

    void foo() {
        print 'none'
    }
}

new X().fo<caret>o('abc')''', PsiMethod)

    assertFalse(method.parameterList.parameters[0].type instanceof PsiEllipsisType)
  }

  void testMethodWithLiteralName() {
    resolveByText('''\
def 'a\\'bc'(){}
"a'b<caret>c"()
''', GrMethod)
  }

  void testValueOf() {
    final valueof = resolveByText('''\
enum MyEnum {
    FOO, BAR
}


MyEnum myEnum
myEnum = MyEnum.va<caret>lueOf('FOO')
''', PsiMethod)

    assertEquals(valueof.parameterList.parametersCount, 1)
  }

  void testResolveOverloadedReturnType() {
    myFixture.addClass('class PsiModifierList {}')
    myFixture.addClass('class GrModifierList extends PsiModifierList {}')
    myFixture.addClass('class GrMember {' +
                       '  GrModifierList get();' +
                       '}')
    myFixture.addClass('class PsiClass {' +
                       '  PsiModifierList get();' +
                       '}')

    myFixture.addClass('class GrTypeDefinition extends PsiClass, GrMember {}')

    final PsiMethod method = resolveByText('new GrTypeDefinition().ge<caret>t()', PsiMethod)

    assertTrue(method.getReturnType().getCanonicalText() == 'GrModifierList')


  }

  void testContradictingPropertyAccessor() {
    def method = resolveByText('''\
class A {
    def setFoo(Object o) {
        print 'method'
    }

    int foo = 5
}

new A().setF<caret>oo(2)
''', PsiMethod)


    assertInstanceOf(method, GrMethodImpl)
  }

  void testContradictingPropertyAccessor2() {
    def method = resolveByText('''\
class A {
    def setFoo(Object o) {
        print 'method'
    }

    int foo = 5
}

new A().f<caret>oo = 2
''', PsiMethod)


    assertInstanceOf(method, GrMethodImpl)
  }

  void testResoleAnonymousMethod() {
    resolveByText('''\
def anon = new Object() {
  def foo() {
    print 2
  }
}

anon.fo<caret>o()
''', GrMethod)
  }

  void testMapAccess() {
    resolveByText('''
      Map<String, List<String>> foo() {}

      foo().bar.first().subs<caret>tring(1, 2)
    ''', PsiMethod)
  }

  void testMixinClosure() {
    resolveByText('''
def foo() {
    def x = { a -> print a}
    Integer.metaClass.abc = { print 'something' }
    1.a<caret>bc()
}
''', PsiMethod)
  }

  void testPreferCategoryMethods() {
    def resolved = resolveByText('''
class TimeCategory {
    public static TimeDuration minus(final Date lhs, final Date rhs) {
        return new TimeDuration()
    }
}

class TimeDuration {}

void bug() {
    use (TimeCategory) {
        def duration = new Date() - new Date()
        print durat<caret>ion
    }
}
''', GrVariable)

    assertEquals("TimeDuration", resolved.typeGroovy.canonicalText)
  }

  void testPreferCategoryMethods2() {
    def resolved = resolveByText('''
class TimeCategory {
    public static TimeDuration minus(final Date lhs, final Date rhs) {
        return new TimeDuration((int) days, hours, minutes, seconds, (int) milliseconds);
    }
}

class TimeDuration {}

void bug() {
    use (TimeCategory) {
        def duration = new Date().minus(new Date())
        print durat<caret>ion
    }
}
''', GrVariable)

    assertEquals("TimeDuration", resolved.typeGroovy.canonicalText)
  }


  void testNegatedIf() {
    resolveByText('''\
def foo(x) {
  if (!(x instanceof String)) return

  x.subst<caret>ring(1)
}
''', PsiMethod)

  }

  void testInferredTypeInsideGStringInjection() {
    resolveByText('''\
class A {}
class B extends A {
    String bar() {'bar'}
}

def foo(A b) {
    if (b instanceof B) {
        doSomethingElse("Message: ${b.ba<caret>r()}")

    }
}
''', PsiMethod)
  }

  void 'test IDEA-110562'() {
    assertNotResolved('''\
interface GrTypeDefinition {
    def baz()
}

class Foo {
    private static void extractSuperInterfaces(Object subclass) {
        if (!(subclass instanceof GrTypeDefinition)) {
            foo()
            subclass.b<caret>az()
        }
    }

    static def foo() {}
}
''')
  }

  private void assertNotResolved(String text) {
    final ref = configureByText(text)
    assertNotNull(ref)
    final resolved = ref.resolve()
    assertNull(resolved)
  }

  void 'test IDEA-110562 2'() {
    resolveByText('''\
interface GrTypeDefinition {
    def baz()
}

class Foo {
    private static void extractSuperInterfaces(Object subclass) {
        if (subclass instanceof GrTypeDefinition) {
            foo()
            subclass.b<caret>az()
        }
    }

    static def foo() {}
}
''', PsiMethod)
  }

  void testInstanceOf1() {
    resolveByText('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o.fo<caret>o() && o.bar()) {
    print o.foo()
  }
}
''', PsiMethod)
  }

  void testInstanceOf2() {
    assertNotResolved('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o.foo() && o.ba<caret>r()) {
    print o.foo()
  }
}
''')
  }

  void testInstanceOf3() {
    resolveByText('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar o.fo<caret>o() && o.bar()) {
    print o.foo()
  }
}
''', PsiMethod)
  }

  void testInstanceOf4() {
    resolveByText('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar o.foo() && o.b<caret>ar()) {
    print o.foo()
  }
}
''', PsiMethod)
  }

  void testInstanceOf5() {
    assertNotResolved('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o.foo() && o.bar()) {
    print o.foo()
  }
  else {
    print o.fo<caret>o()
  }
}
''')
  }

  void testInstanceOf6() {
    assertNotResolved('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar && o.foo() && o.bar()) {
    print o.foo()
  }
  else {
    print o.fo<caret>o()
  }
}
''')
  }

  void testInstanceOf7() {
    assertNotResolved('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar && o.foo() && o.bar()) {
    print o.foo()
  }
  else {
    print o.ba<caret>r()
  }
}
''')
  }

  void testBinaryWithQualifiedRefsInArgs() {
    GrBinaryExpression expr = configureByText('_.groovy', '''\
class Base {
    def or(String s) {}
    def or(Base b) {}

    public static Base SHOW_NAME = new Base()
    public static Base SHOW_TYPE = new Base()
}

class GrTypeDefinition  {
    def foo() {
        print (Base.SHOW_NAME <caret>| Base.SHOW_TYPE)

    }
}
''', GrBinaryExpression)

    assert expr.multiResolve(false).length == 1
    assert expr.multiResolve(true).length > 1
  }

  void testStaticMethodInInstanceContext() {
    GrMethod resolved = resolveByText('''\
class Foo {
    def foo(String s){}
    static def foo(File f){}
}

new Foo().f<caret>oo(new File(''))
''', GrMethod)

    assertTrue(resolved.hasModifierProperty(PsiModifier.STATIC))
  }

  void testBaseScript() {
    addBaseScript()

    myFixture.addClass '''
class CustomScript extends Script {
  void foo() {}
}'''

    resolveByText('''
import groovy.transform.BaseScript

@BaseScript
CustomScript myScript;

f<caret>oo()
''', PsiMethod)
  }

  void testImportStaticVSDGM() {
    def method = resolveByText('''
import static Bar.is

class Foo {
    void foo() {
        i<caret>s(null)
    }
}

class Bar {
    static boolean is(Class c) {
        println 'bar'
        return true
    }
}
''', PsiMethod)

    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals('Bar', clazz.qualifiedName)

  }

  void testImportStaticPrint() {
    def print = resolveByText('''
import static C.print

new Runnable() {
    void run() {
        pri<caret>nt "wow";
    }
}.run()

class C {
    static def print(String s) {print 'hjk'}
}
''', PsiMethod)


    PsiClass clazz = print.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  void testPrintInClosure() {
    def print = resolveByText('''
class C {
    static def print(String s) {prin<caret>t 'hjk'}
}
''', PsiMethod)


    PsiClass clazz = print.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  void testPrint() {
    def print = resolveByText('''
import static C.print

def cl = {pr<caret>int 'abc'}

class C {
    static def print(String s) {print 'hjk'}
}
''', PsiMethod)


    PsiClass clazz = print.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  void testScriptMethodVSStaticImportInsideAnonymous() {
    def method = resolveByText '''
import static C.abc

class C {
    static def abc(c) {
        print 2
    }
}
new Runnable() {
    @Override
    void run() {
        ab<caret>c '2'
    }
}.run()

def abc(String s) { print 'hjk' }
''', PsiMethod
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }


  //IDEA-125331
  void _testScriptMethodVSStaticImportInsideClosure() {
    def method = resolveByText '''
import static C.abc

class C {
    static def abc(c) {
        print 2
    }
}
def cl = {
    ab<caret>c '2'
}

def abc(String s) { print 'hjk' }
''', PsiMethod
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  void testScriptMethodVSStaticImportInsideScript() {
    def method = resolveByText '''
import static C.abc

class C {
    static def abc(c) {
        print 2
    }
}

ab<caret>c '2'

def abc(String s) { print 'hjk' }
''', PsiMethod
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertInstanceOf(clazz, GroovyScriptClass)
  }


  void testLocalStringVSDefault() {
    def clazz = resolveByText('''
class String {}

new Str<caret>ing()
''', PsiClass)

    assertEquals("String", clazz.qualifiedName)
  }

  void testLocalVarVSStaticImport() {
    resolveByText('''
import static Abc.foo

class Abc {
    static def foo() { print 'static' }
}

def foo =  { print 'closure' }


fo<caret>o()
''', GrVariable)
  }

  void testInstanceMethodVSStaticImport() {
    def method = resolveByText('''
import static C.abc

class C {
    static def abc(a) {}
}

class B {
    def abc(a) {}

    void bar() {
       a<caret>bc(x)
    }
}
''', PsiMethod)
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals('C', clazz.qualifiedName)
  }

  void testUseVSStaticImport() {
    def method = resolveByText('''
import static C.abc

class C {
    static def abc(c) {
        print 'static '
    }
}

class D {
    static def abc(c, d) {
        print 'mixin'
    }
}

class Fo {
    def bar() {
        use(D) {
            ab<caret>c(2)
        }
    }
}
''', PsiMethod)
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals('C', clazz.qualifiedName)
  }

  void testSuperReferenceWithTraitQualifier() {
    def method = resolveByText('''
trait A {
    String exec() { 'A' }
}
trait B {
    String exec() { 'B' }
}

class C implements A,B {
    String exec() {A.super.exe<caret>c() }
}
''', PsiMethod)
    assertTrue(method.containingClass.name == 'A')
  }

  void testSuperReferenceWithTraitQualifier2() {
    def method = resolveByText('''
trait A {
    String exec() { 'A' }
}
trait B {
    String exec() { 'B' }
}

class C implements A, B {
    String exec() {B.super.exe<caret>c() }
}
''', PsiMethod)
    assertTrue(method.containingClass.name == 'B')
  }

  void testClashingTraitMethods() {
    def method = resolveByText('''
trait A {
    String exec() { 'A' }
}
trait B {
    String exec() { 'B' }
}

class C implements A, B {
    String foo() {exe<caret>c() }
}
''', GrTraitMethod)
    assertEquals("B", method.prototype.containingClass.name)
  }

  void testTraitMethodFromAsOperator1() {
    resolveByText('''
trait A {
  def foo(){}
}
class B {
  def bar() {}
}

def v = new B() as A
v.fo<caret>o()
''', PsiMethod)
  }

  void testTraitMethodFromAsOperator2() {
    resolveByText('''
trait A {
  def foo(){}
}
class B {
  def bar() {}
}

def v = new B() as A
v.ba<caret>r()
''', PsiMethod)
  }

  void testMethodReferenceWithDefaultParameters() {
    resolveByText('''
class X {
  def foo(def it = null) {print it}

  def bar() {
    print this.&f<caret>oo
  }
}
''', PsiMethod)
  }

  void 'test static trait method generic return type'() {
    def method = resolveByText('''
trait GenericSourceTrait<E> {
    static E someOtherStaticMethod() {null}
}
class SourceConcrete implements GenericSourceTrait<String> {})
SourceConcrete.someOtherStatic<caret>Method()
''', GrTraitMethod)
    assertEquals "java.lang.String", method.returnType.canonicalText
  }

  void 'test substitutor is not computed within resolve'() {
    def ref = configureByText('_.groovy', '''
[1, 2, 3].with {
  group<caret>By({2})
}
''', GrReferenceExpression)
    def results = ref.multiResolve(false)
    assert results.length > 0
    results.each {
      assert it instanceof GroovyMethodResult
      def computer = it.substitutorComputer
      assert computer instanceof NotNullCachedComputableWrapper
      assert !computer.computed
    }
  }
}
