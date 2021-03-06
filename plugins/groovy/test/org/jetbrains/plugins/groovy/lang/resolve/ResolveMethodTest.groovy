// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.*
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.*
import org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference
import org.jetbrains.plugins.groovy.util.GroovyLatestTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Ignore
import org.junit.Test

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import static org.jetbrains.plugins.groovy.LightGroovyTestCase.assertType
import static org.junit.Assert.*

@CompileStatic
class ResolveMethodTest extends GroovyLatestTest implements ResolveTest {

  ResolveMethodTest() {
    super("resolve/method")
  }

  @NotNull
  private PsiReference configureByFile(@NonNls String filePath) {
    fixture.configureByFile(testName + "/" + filePath)
    referenceUnderCaret(PsiReference)
  }

  @Nullable
  private PsiElement resolve(String fileName) {
    configureByFile(fileName).resolve()
  }

  @Test
  void staticImport3() {
    fixture.copyFileToProject(testName + '/org/Shrimp.groovy', 'org/Shrimp.groovy')
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 1)
    assertEquals(((GrMethod)resolved).name, "isShrimp")
  }

  @Test
  void staticImport() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Ignore("static imports with non fully qualified names are not supported")
  @Test
  void importStaticReverse() {
    PsiReference ref = configureByFile("ImportStaticReverse.groovy")
    assertNotNull(ref.resolve())
  }

  @Test
  void simple() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 1)
  }

  @Test
  void varargs() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 1)
  }

  @Test
  void byName() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
  }

  @Test
  void byName1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 2)
  }

  @Test
  void byNameVarargs() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 1)
  }

  @Test
  void parametersNumber() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(((GrMethod)resolved).parameters.length, 2)
  }

  @Test
  void filterBase() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
    assertEquals(1, ref.multiResolve(false).length)
  }

  @Test
  void twoCandidates() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNull(ref.resolve())
    assertEquals(2, ref.multiResolve(false).length)
  }

  @Test
  void defaultMethod1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrGdkMethod)
  }

  @Test
  void defaultStaticMethod() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrGdkMethod)
    assertTrue(((GrGdkMethodImpl)resolved).hasModifierProperty(PsiModifier.STATIC))
  }

  @Test
  void primitiveSubtyping() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrGdkMethod)
    assertTrue(((GrGdkMethodImpl)resolved).hasModifierProperty(PsiModifier.STATIC))
  }

  @Test
  void defaultMethod2() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertTrue(resolved instanceof GrGdkMethod)
  }

  @Test
  void grvy111() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertTrue(resolved instanceof GrGdkMethod)
    assertEquals(0, ((PsiMethod)resolved).parameterList.parametersCount)
    assertTrue(((PsiMethod)resolved).hasModifierProperty(PsiModifier.PUBLIC))
  }

  @Test
  void scriptMethod() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertEquals("groovy.lang.Script", ((PsiMethod)resolved).containingClass.qualifiedName)
  }

  @Test
  void arrayDefault() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertTrue(resolved instanceof GrGdkMethod)
  }

  @Test
  void arrayDefault1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertTrue(resolved instanceof GrGdkMethod)
  }

  @Test
  void spreadOperator() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    GrMethodCallExpression methodCall = (GrMethodCallExpression)ref.element.parent
    PsiType type = methodCall.type
    assertTrue(type instanceof PsiClassType)
    PsiClass clazz = ((PsiClassType)type).resolve()
    assertNotNull(clazz)
    assertEquals("java.util.ArrayList", clazz.qualifiedName)
  }

  @Test
  void langClass() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertEquals("java.lang.Class", ((PsiMethod)resolved).containingClass.qualifiedName)
  }

  @Test
  void complexOverload() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  //test we don't resolve to field in case explicit getter is present
  @Test
  void fromGetter() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void overload1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    assertEquals("java.io.Serializable", ((PsiMethod)resolved).parameterList.parameters[0].type.canonicalText)
  }

  @Test
  void constructor() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(resolved)
    assertTrue(resolved.constructor)
  }

  @Test
  void constructor1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(method)
    assertTrue(method.constructor)
    assertEquals(0, method.parameterList.parameters.length)
  }

  @Test
  void constructor2() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNull(method)
  }

  //grvy-101
  @Test
  void constructor3() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod method = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(method)
    assertTrue(method.constructor)
    assertEquals(0, method.parameterList.parameters.length)
  }

  @Test
  void wrongConstructor() {
    fixture.addFileToProject('Classes.groovy', 'class Foo { int a; int b }')
    def ref = referenceByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression)ref.element.parent).advancedResolve().element instanceof DefaultConstructor
  }

  @Test
  void langImmutableConstructor() {
    fixture.addFileToProject('Classes.groovy', '@Immutable class Foo { int a; int b }')
    def ref = referenceByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression)ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  @Test
  void transformImmutableConstructor() {
    fixture.addFileToProject('Classes.groovy', '@groovy.transform.Immutable class Foo { int a; int b }')
    def ref = referenceByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression)ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  @Test
  void tupleConstructor() {
    fixture.addFileToProject('Classes.groovy', '@groovy.transform.TupleConstructor class Foo { int a; final int b }')
    def ref = referenceByText('new Fo<caret>o(2, 3)')
    def target = ((GrNewExpression)ref.element.parent).advancedResolve().element
    assert target instanceof PsiMethod
    assert ((PsiMethod)target).parameterList.parametersCount == 2
    assert target.navigationElement instanceof PsiClass
  }

  @Test
  void canonicalConstructor() {
    fixture.addFileToProject('Classes.groovy', '@groovy.transform.Canonical class Foo { int a; int b }')
    def ref = referenceByText('new Fo<caret>o(2, 3)')
    assert ((GrNewExpression)ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  @Test
  void inheritConstructors() {
    fixture.addFileToProject('Classes.groovy', '@groovy.transform.InheritConstructors class CustomException extends Exception {}')
    def ref = referenceByText('new Cu<caret>stomException("msg")')
    assert ((GrNewExpression)ref.element.parent).advancedResolve().element instanceof PsiMethod
  }

  @Test
  void partiallyDeclaredType() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void generic1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void notAField() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void escapedReferenceExpression() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void listOfClasses() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void emptyVsMap() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(resolved)
    assertEquals(0, resolved.parameterList.parametersCount)
  }

  @Test
  void privateScriptMethod() {
    PsiReference ref = configureByFile("A.groovy")
    assertNotNull(ref.resolve())
  }

  @Test
  void aliasedConstructor() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(resolved)
    assertEquals("JFrame", resolved.name)
  }


  @Test
  void fixedVsVarargs1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiMethod resolved = ((GrNewExpression)ref.element.parent).resolveMethod()
    assertNotNull(resolved)
    final GrParameter[] parameters = ((GrMethod)resolved).parameters
    assertEquals(parameters.length, 1)
    assertEquals(parameters[0].type.canonicalText, "int")
  }

  @Test
  void fixedVsVarargs2() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
    final PsiParameter[] parameters = ((PsiMethod)resolved).parameterList.parameters
    assertEquals(parameters.length, 2)
    assertEquals(parameters[0].type.canonicalText, "java.lang.Class")
  }

  @Test
  void reassigned1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    final GrParameter[] parameters = ((GrMethod)resolved).parameters
    assertEquals(parameters.length, 1)
    assertEquals(parameters[0].type.canonicalText, "java.lang.String")
  }

  @Test
  void reassigned2() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    final GrParameter[] parameters = ((GrMethod)resolved).parameters
    assertEquals(parameters.length, 1)
    assertEquals(parameters[0].type.canonicalText, "int")
  }

  @Test
  void generics1() {
    PsiReference ref = configureByFile("A.groovy")
    assertNotNull(ref.resolve())
  }

  @Test
  void genericOverriding() {
    PsiReference ref = configureByFile("A.groovy")
    assertNotNull(ref.resolve())
  }

  @Test
  void useOperator() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrGdkMethod)
  }

  @Test
  void closureMethodInsideClosure() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void scriptMethodInsideClosure() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void explicitGetter() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertNotNull(resolved)
    assertFalse(resolved instanceof GrAccessorMethod)
  }

  @Test
  void groovyAndJavaSamePackage() {
    fixture.copyFileToProject(testName + "/p/Hu.java", "p/Hu.java")
    PsiReference ref = configureByFile("p/Ha.groovy")
    assertTrue(ref.resolve() instanceof PsiMethod)
  }

  @Test
  void unboxBigDecimal() {
    fixture.addClass("package java.math; public class BigDecimal {}")
    def ref = (GroovyReference)referenceByText('java.lang.Math.<caret>min(0, 0.0)')
    def results = ref.resolve(false)
    assert results.size() == 2
  }

  @Test
  void grvy1157() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void grvy1173() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void grvy1173_a() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void grvy1218() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void methodPointer1() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void methodPointer2() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof PsiMethod)
  }

  @Test
  void methodCallTypeFromMultiResolve() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNull(ref.resolve())
    assertTrue(((GrMethodCallExpression)ref.parent).type.equalsToText("java.lang.String"))
  }

  @Test
  void defaultOverloaded() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void defaultOverloaded2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void defaultOverloaded3() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void multipleAssignment1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void multipleAssignment2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void multipleAssignment3() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void closureIntersect() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void closureCallCurry() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void superFromGString() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("SuperFromGString.groovy").element
    assertNotNull(ref.resolve())
  }

  @Test
  void nominalTypeIsBetterThanNull() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").element
    final PsiType type = assertInstanceOf(ref.resolve(), GrMethod.class).inferredReturnType
    assertNotNull(type)
    assertTrue(type.equalsToText(CommonClassNames.JAVA_LANG_STRING))
  }

  @Test
  void qualifiedSuperMethod() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals("SuperClass", ((GrMethod)resolved).containingClass.name)
  }

  @Test
  void qualifiedThisMethod() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals("OuterClass", ((GrMethod)resolved).containingClass.name)
  }

  @Test
  void printMethodInAnonymousClass1() {
    PsiReference ref = configureByFile("A.groovy")
    assertInstanceOf(ref.resolve(), GrGdkMethod.class)
  }

  @Test
  void printMethodInAnonymousClass2() {
    PsiReference ref = configureByFile("B.groovy")
    assertInstanceOf(ref.resolve(), GrGdkMethod.class)
  }

  @Test
  void substituteWhenDisambiguating() {
    fixture.configureByText "a.groovy", """
class Zoo {
  def Object find(Object x) {}
  def <T> T find(Collection<T> c) {}

  {
    fin<caret>d(["a"])
  }

}"""
    def ref = fixture.file.findReferenceAt(fixture.editor.caretModel.offset)
    assertEquals 1, ((PsiMethod)ref.resolve()).typeParameters.length
  }

  @Test
  void fooMethodInAnonymousClass() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
    assertEquals("A", ((PsiMethod)resolved).containingClass.name)
  }

  @Test
  void optionalParameters1() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void optionalParameters2() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void optionalParameters3() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void optionalParameters4() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void notInitializedVariable() {
    PsiReference ref = configureByFile("A.groovy")
    final PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod.class)
  }

  @Test
  void methodVsField() {
    final PsiReference ref = configureByFile("A.groovy")
    final PsiElement element = ref.resolve()
    assertInstanceOf(element, PsiMethod.class)
  }

  @Test
  void localVariableVsGetter() {
    final PsiReference ref = configureByFile("A.groovy")
    final PsiElement element = ref.resolve()
    assertInstanceOf(element, GrVariable.class)
  }

  @Test
  void invokeMethodViaThisInStaticContext() {
    final PsiReference ref = configureByFile("A.groovy")
    final PsiElement element = ref.resolve()
    assertEquals "Class", assertInstanceOf(element, PsiMethod).containingClass.name
  }

  @Test
  void invokeMethodViaClassInStaticContext() {
    final PsiReference ref = configureByFile("A.groovy")
    final PsiElement element = ref.resolve()
    assertInstanceOf(element, PsiMethod.class)
    assertEquals "Foo", assertInstanceOf(element, PsiMethod).containingClass.name
  }

  @Test
  void useInCategory() {
    PsiReference ref = configureByFile("A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  @Test
  void methodVsLocalVariable() {
    PsiReference ref = configureByFile("A.groovy")
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrVariable
  }

  @Test
  void commandExpressionStatement1() {
    PsiElement method = resolve("A.groovy")
    assertEquals "foo2", assertInstanceOf(method, GrMethod).name
  }

  @Test
  void commandExpressionStatement2() {
    PsiElement method = resolve("A.groovy")
    assertEquals "foo3", assertInstanceOf(method, GrMethod).name
  }

  @Test
  void upperCaseFieldAndGetter() {
    assertTrue resolve("A.groovy") instanceof GrMethod
  }

  @Test
  void upperCaseFieldWithoutGetter() {
    assertTrue resolve("A.groovy") instanceof GrAccessorMethod
  }

  @Test
  void spreadOperatorNotList() {
    assertInstanceOf resolve("A.groovy"), GrMethod
  }

  @Test
  void methodChosenCorrect() {
    final PsiElement resolved = resolve("A.groovy")
    assert "map" == assertInstanceOf(resolved, GrMethod).parameterList.parameters[0].name
  }

  @Test
  void resolveCategories() {
    assertNotNull resolve("A.groovy")
  }

  @Test
  void resolveValuesOnEnum() {
    assertNotNull resolve("A.groovy")
  }

  @Test
  void avoidResolveLockInClosure() {
    assertNotNull resolve("A.groovy")
  }

  @Test
  void asType() {
    assertInstanceOf resolve("A.groovy"), GrMethod
  }

  @Test
  void plusAssignment() {
    final PsiElement resolved = resolve("A.groovy")
    assertEquals("plus", assertInstanceOf(resolved, GrMethod).name)
  }

  @Test
  void wrongGdkCallGenerics() {
    fixture.configureByText("a.groovy",
                            "Map<File,String> map = [:]\n" +
                            "println map.ge<caret>t('', '')"
    )
    def ref = fixture.file.findReferenceAt(fixture.editor.caretModel.offset)
    assertInstanceOf ref.resolve(), GrGdkMethod
  }

  @Test
  void staticImportInSamePackage() {
    fixture.addFileToProject "pack/Foo.groovy", """package pack
class Foo {
  static def foo()
}"""
    PsiReference ref = configureByFile("A.groovy")
    assertNotNull(ref.resolve())
  }

  @Test
  void stringRefExpr1() {
    assertNotNull(resolve("a.groovy"))
  }

  @Test
  void stringRefExpr2() {
    assertNotNull(resolve("a.groovy"))
  }

  @Test
  void stringRefExpr3() {
    assertNotNull(resolve("a.groovy"))
  }

  @Test
  void nestedWith() {
    assertNotNull(resolve('a.groovy'))
  }

  @Test
  void category() {
    assertNotNull(resolve('a.groovy'))
  }

  @Test
  void dontUseQualifierScopeInDGM() {
    assertNull resolve('a.groovy')
  }

  @Test
  void inferPlusType() {
    assertNotNull(resolve('a.groovy'))
  }

  @Test
  void mixinAndCategory() {
    def ref = referenceByText("""
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
    assertInstanceOf(((GrGdkMethod)resolved).staticMethod, GrReflectedMethod)
  }

  @Test
  void onlyMixin() {
    def ref = referenceByText("""
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

  @Test
  void twoMixinsInModifierList() {
    def ref = referenceByText("""
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


  @Test
  void disjunctionType() {
    def ref = referenceByText("""
import java.sql.SQLException
def test() {
        try {}
        catch (IOException | SQLException ex) {
            ex.prin<caret>tStackTrace();
        }
}""")
    assertNotNull(ref.resolve())
  }


  @Test
  void stringInjectionDontOverrideItParameter() {
    def ref = referenceByText("""
[2, 3, 4].collect {"\${it.toBigDeci<caret>mal()}"}
""")
    assertNotNull(ref.resolve())
  }

  @Test
  void publicVsPrivateConstructor() {
    def resolved = (referenceByText('throw new Assertion<caret>Error("foo")').element.parent as GrNewExpression).resolveMethod()
    assertNotNull resolved

    PsiParameter[] parameters = resolved.parameterList.parameters
    assertTrue parameters.length == 1
    assertType("java.lang.String", parameters[0].type)
  }

  @Test
  void scriptMethodsInClass() {
    def ref = referenceByText('''
class X {
  def foo() {
    scriptMetho<caret>d('1')
  }
}
def scriptMethod(String s){}
''')

    assertNull(ref.resolve())
  }

  @Test
  void staticallyImportedMethodsVsDGMMethods() {
    fixture.addClass('''\
package p;
public class Matcher{}
''')
    fixture.addClass('''\
package p;
class Other {
  public static Matcher is(Matcher m){}
  public static Matcher create(){}
}''')

    def ref = referenceByText('''\
import static p.Other.is
import static p.Other.create

i<caret>s(create())

''')

    def resolved = assertInstanceOf(ref.resolve(), PsiMethod)
    assertEquals 'Other', resolved.containingClass.name
  }

  @Test
  void staticallyImportedMethodsVsCurrentClassMethod() {
    fixture.addClass('''\
package p;
class Other {
  public static Object is(Object m){}
}''')

    def ref = referenceByText('''\
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

  @Test
  void inapplicableStaticallyImportedMethodsVsCurrentClassMethod() {
    fixture.addClass('''\
package p;
class Other {
  public static Object is(String m){}
}''')

    def ref = referenceByText('''\
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

  @Test
  void inferArgumentTypeFromMethod1() {
    def ref = referenceByText('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)

    a.subst<caret>ring(2)
}
''')
    assertNotNull(ref.resolve())
  }

  @Test
  void inferArgumentTypeFromMethod2() {
    def ref = referenceByText('''\
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

  @Test
  void inferArgumentTypeFromMethod3() {
    def ref = referenceByText('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)

    a.int<caret>Value()
}
''')
    assertNotNull(ref.resolve())
  }

  @Test
  void inferArgumentTypeFromMethod4() {
    def ref = referenceByText('''\
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

  @Test
  void staticImportFromSuperClass() {
    def ref = referenceByText('''\
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

  @Test
  void usageOfStaticImportFromSuperClass() {
    def ref = referenceByText('''\
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

  @Test
  void mixin() {
    def ref = referenceByText('''\
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


  @Test
  void groovyExtensions() {
    def ref = referenceByText('''\
package pack

class StringExt {
  static sub(String s) {}
}

"".su<caret>b()''')

    fixture.addFileToProject("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", """\
extensionClasses=\\
  pack.StringExt
""")

    assertNotNull(ref.resolve())
  }

  @Test
  void initializerOfScriptField() {
    def ref = referenceByText('''\
import groovy.transform.Field

def xx(){5}

@Field
def aa = 5 + x<caret>x()
''')
    assertInstanceOf(ref.resolve(), GrMethod)
  }

  @Test
  void runtimeMixin1() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

metaClass.mixin(Foo)
d<caret>oSmth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin2() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

this.metaClass.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin3() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.metaClass.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin4() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.class.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin5() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.mixin(Foo)
do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin6() {
    resolveTest('''\
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

  @Test
  void runtimeMixin7() {
    resolveTest('''\
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

  @Test
  void runtimeMixin8() {
    resolveTest('''\
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

  @Test
  void runtimeMixin9() {
    resolveTest('''\
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

  @Test
  void runtimeMixin10() {
    resolveTest('''\
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

  @Test
  void runtimeMixin11() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

metaClass.mixin(Foo)
new _a().d<caret>oSmth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin12() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

this.metaClass.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin13() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.metaClass.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin14() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.class.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin15() {
    resolveTest('''\
class Foo {
    public static void doSmth(Script u) {
        println "hello"
    }
}

_.mixin(Foo)
new _a().do<caret>Smth()
''', PsiMethod)
  }

  @Test
  void runtimeMixin16() {
    resolveTest('''\
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

  @Test
  void runtimeMixin17() {
    resolveTest('''\
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

  @Test
  void runtimeMixin18() {
    resolveTest('''\
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

  @Test
  void runtimeMixin19() {
    resolveTest('''\
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

  @Test
  void runtimeMixin20() {
    resolveTest('''\
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

  @Test
  void runtimeMixin21() {
    resolveTest('''\
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

  @Test
  void runtimeMixin22() {
    resolveTest '''\
class ReentrantLock {}

ReentrantLock.metaClass.withLock = { nestedCode -> }

new ReentrantLock().withLock {
    fo<caret>o(3)
}
''', null
  }

  @Test
  void runtimeMixin23() {
    assertNotNull resolveTest('''\
class ReentrantLock {}

ReentrantLock.metaClass.withLock = { nestedCode -> }

new ReentrantLock().withLock {
    withL<caret>ock(2)
}
''', PsiElement)
  }

  @Test
  void runnableVsCallable() {
    final PsiMethod method = resolveTest('''\
import java.util.concurrent.Callable


void bar(Runnable c) {}

void bar(Callable<?> c) {}

b<caret>ar {
    print 2
}

''', PsiMethod)

    assertTrue(method.parameterList.parameters[0].type.equalsToText('java.lang.Runnable'))
  }

  @Test
  void oneArgVsEllipsis1() {
    def method = resolveTest('''\
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

  @Test
  void oneArgVsEllipsis2() {
    def method = resolveTest('''\
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

  @Test
  void methodWithLiteralName() {
    resolveTest('''\
def 'a\\'bc'(){}
"a'b<caret>c"()
''', GrMethod)
  }

  @Test
  void valueOf() {
    final valueof = resolveTest('''\
enum MyEnum {
    FOO, BAR
}


MyEnum myEnum
myEnum = MyEnum.va<caret>lueOf('FOO')
''', PsiMethod)

    assertEquals(valueof.parameterList.parametersCount, 1)
  }

  @Ignore("groovy actually doesn't care about return type, TODO check this")
  @Test
  void resolveOverloadedReturnType() {
    fixture.addClass('class PsiModifierList {}')
    fixture.addClass('class GrModifierList extends PsiModifierList {}')
    fixture.addClass('class GrMember {' +
                     '  GrModifierList get();' +
                     '}')
    fixture.addClass('class PsiClass {' +
                     '  PsiModifierList get();' +
                     '}')

    fixture.addClass('class GrTypeDefinition extends PsiClass, GrMember {}')

    final PsiMethod method = resolveTest('new GrTypeDefinition().ge<caret>t()', PsiMethod)

    assertTrue(method.getReturnType().getCanonicalText() == 'GrModifierList')
  }

  @Test
  void contradictingPropertyAccessor() {
    def method = resolveTest('''\
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

  @Test
  void contradictingPropertyAccessor2() {
    def method = resolveTest('''\
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

  @Test
  void resoleAnonymousMethod() {
    resolveTest('''\
def anon = new Object() {
  def foo() {
    print 2
  }
}

anon.fo<caret>o()
''', GrMethod)
  }

  @Test
  void mapAccess() {
    resolveTest('''
      Map<String, List<String>> foo() {}

      foo().bar.first().subs<caret>tring(1, 2)
    ''', PsiMethod)
  }

  @Test
  void mixinClosure() {
    resolveTest('''
def foo() {
    def x = { a -> print a}
    Integer.metaClass.abc = { print 'something' }
    1.a<caret>bc()
}
''', PsiMethod)
  }

  @Test
  void preferCategoryMethods() {
    def resolved = resolveTest('''
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

  @Test
  void preferCategoryMethods2() {
    def resolved = resolveTest('''
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


  @Test
  void negatedIf() {
    resolveTest('''\
def foo(x) {
  if (!(x instanceof String)) return

  x.subst<caret>ring(1)
}
''', PsiMethod)
  }

  @Test
  void inferredTypeInsideGStringInjection() {
    resolveTest('''\
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

  @Test
  void 'IDEA-110562'() {
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
    final ref = referenceByText(text)
    assertNotNull(ref)
    final resolved = ref.resolve()
    assertNull(resolved)
  }

  @Test
  void 'IDEA-110562 2'() {
    resolveTest('''\
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

  @Test
  void instanceOf1() {
    resolveTest('''\
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

  @Test
  void instanceOf2() {
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

  @Test
  void instanceOf3() {
    resolveTest('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar && o.fo<caret>o() && o.bar()) {
    print o.foo()
  }
}
''', PsiMethod)
  }

  @Test
  void instanceOf4() {
    resolveTest('''\
class Foo {
  def foo(){}
}

class Bar {
  def bar()
}

def bar(Object o) {
  if (o instanceof Foo && o instanceof Bar && o.foo() && o.b<caret>ar()) {
    print o.foo()
  }
}
''', PsiMethod)
  }

  @Test
  void instanceOf5() {
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

  @Test
  void instanceOf6() {
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

  @Test
  void instanceOf7() {
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

  @Test
  void binaryWithQualifiedRefsInArgs() {
    GrOperatorReference ref = referenceByText('''\
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
''', GrOperatorReference)

    assert ref.multiResolve(false).length == 1
    assert ref.multiResolve(true).length > 1
  }

  @Test
  void staticMethodInInstanceContext() {
    GrMethod resolved = resolveTest('''\
class Foo {
    def foo(String s){}
    static def foo(File f){}
}

new Foo().f<caret>oo(new File(''))
''', GrMethod)

    assertTrue(resolved.hasModifierProperty(PsiModifier.STATIC))
  }

  @Test
  void baseScript() {
    fixture.addClass 'class CustomScript extends Script { void foo() {} }'
    resolveTest('''
import groovy.transform.BaseScript

@BaseScript
CustomScript myScript;

f<caret>oo()
''', PsiMethod)
  }

  @Ignore("requires overhaul in static import resolution")
  @Test
  void importStaticVSDGM() {
    def method = resolveTest('''
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

  @Test
  void importStaticPrint() {
    def print = resolveTest('''
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

  @Test
  void printInClosure() {
    def print = resolveTest('''
class C {
    static def print(String s) {prin<caret>t 'hjk'}
}
''', PsiMethod)


    PsiClass clazz = print.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  @Test
  void print() {
    def print = resolveTest('''
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

  @Test
  void scriptMethodVSStaticImportInsideAnonymous() {
    def method = resolveTest '''
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


  @Test
  void scriptMethodVSStaticImportInsideClosure() {
    def method = resolveTest '''
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

  @Test
  void scriptMethodVSStaticImportInsideLambda() {
    def method = resolveTest '''
import static C.abc

class C {
    static def abc(c) {
        print 2
    }
}
def cl = () -> {
    ab<caret>c '2'
}

def abc(String s) { print 'hjk' }
''', PsiMethod
    PsiClass clazz = method.containingClass
    assertNotNull(clazz)
    assertEquals("C", clazz.qualifiedName)
  }

  @Test
  void scriptMethodVSStaticImportInsideScript() {
    def method = resolveTest '''
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

  @Test
  void localStringVSDefault() {
    def clazz = resolveTest('''
class String {}

new Str<caret>ing()
''', PsiClass)

    assertEquals("String", clazz.qualifiedName)
  }

  @Test
  void localVarVSStaticImport() {
    resolveTest('''
import static Abc.foo

class Abc {
    static def foo() { print 'static' }
}

def foo =  { print 'closure' }


fo<caret>o()
''', GrVariable)
  }

  @Test
  void instanceMethodVSStaticImport() {
    def method = resolveTest('''
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
    assertEquals('B', clazz.qualifiedName)
  }

  @Test
  void useVSStaticImport() {
    def method = resolveTest('''
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

  @Test
  void superReferenceWithTraitQualifier() {
    def method = resolveTest('''
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

  @Test
  void superReferenceWithTraitQualifier2() {
    def method = resolveTest('''
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

  @Test
  void clashingTraitMethods() {
    def method = resolveTest('''
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

  @Test
  void traitMethodFromAsOperator1() {
    resolveTest('''
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

  @Test
  void traitMethodFromAsOperator2() {
    resolveTest('''
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

  @Test
  void methodReferenceWithDefaultParameters() {
    def ref = referenceByText('''
class X {
  def foo(def it = null) {print it}

  def bar() {
    print this.&f<caret>oo
  }
}
''')
    def results = ref.multiResolve(false)
    assert results.length == 2
    for (result in results) {
      assert result.element instanceof GrReflectedMethod
      assert result.validResult
    }
  }

  @Test
  void 'static trait method generic return type'() {
    def method = resolveTest('''
trait GenericSourceTrait<E> {
    static E someOtherStaticMethod() {null}
}
class SourceConcrete implements GenericSourceTrait<String> {}
SourceConcrete.someOtherStatic<caret>Method()
''', GrTraitMethod)
    assertEquals "java.lang.String", method.returnType.canonicalText
  }

  @Test
  void 'resolve method with class qualifier'() {
    fixture.addClass '''\
package foo.bar;

public class A {
  public static void foo() {}
  public static String getCanonicalName() {return "";}
}
'''
    def data = [
      'A.fo<caret>o()'              : 'foo.bar.A',
      'A.class.fo<caret>o()'        : 'foo.bar.A',
      'A.simpleN<caret>ame'         : 'java.lang.Class',
      'A.class.simpleN<caret>ame'   : 'java.lang.Class',
      'A.canonicalN<caret>ame'      : 'foo.bar.A',
      'A.class.canonicalN<caret>ame': 'foo.bar.A'
    ]
    data.each { expression, expectedClass ->
      def ref = referenceByText("import foo.bar.A; $expression")
      def element = ref.resolve()
      assert element instanceof PsiMember: "$expression -> $expectedClass"
      assert ((PsiMember)element).containingClass.qualifiedName == expectedClass
    }
  }

  @Test
  void 'low priority for varargs method'() {
    def method = resolveTest('''\
def foo(Object... values) {}
def foo(Object[] values, Closure c) {}

fo<caret>o(new Object[0], {})
''', GrMethod)
    assert !method.isVarArgs()
    assert method.parameters.size() == 2
  }

  @Test
  void 'compareTo() with Integer and BigDecimal'() {
    fixture.addClass('package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}')
    resolveTest('''\
BigDecimal b = 1
1.comp<caret>areTo(b)
1 > b
''', GrGdkMethod)
    fixture.enableInspections GroovyAssignabilityCheckInspection
    fixture.checkHighlighting()
  }

  @Test
  void 'resolve AutoImplement'() {
    def method = resolveTest '''
import groovy.transform.AutoImplement

@AutoImplement
class SomeClass extends List<Integer> {
}

new SomeClass().si<caret>ze()
''', GrLightMethodBuilder
    assert (method as GrLightMethodBuilder).originInfo.contains("@AutoImplement")
  }

  @Test
  void 'resolve AutoImplement implemented'() {
    resolveTest '''
import groovy.transform.AutoImplement

@AutoImplement
class SomeClass extends List<Integer> {
  @Override
  public int size() {return 0}
}

new SomeClass().si<caret>ze()
''', GrMethodImpl
  }

  @Test
  void 'prefer varargs in no-arg call'() {
    def file = fixture.configureByText('_.groovy', '''\
class A {
  A(String... a) { println "varargs" }
  A(A a) { println "single" }
}

new A()
''') as GroovyFile
    def expression = file.statements.last() as GrNewExpression
    def resolved = expression.resolveMethod()
    assert resolved instanceof GrMethod
    assert resolved.isVarArgs()
  }

  @Test
  void 'static method via class instance'() {
    resolveTest '''\
class A { public static foo() { 45 } }
def a = A // class instance
a.<caret>foo()
''', GrMethod
  }

  @Test
  void 'array vs single with simple argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object[] values) {}
static void usage(String label) { <caret>foo(label) }
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object')
  }

  @Test
  void 'array vs single with array argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object[] values) {}
static void usage(String[] label) { <caret>foo(label) }
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object[]')
  }

  @Test
  void 'array vs single with null argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object[] values) {}
<caret>foo(null)
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object')
  }

  @Test
  void 'vararg vs single with array argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object... values) {}
static usage(String[] label) { <caret>foo(label) }
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object...')
  }

  @Test
  void 'vararg vs single with simple argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object... values) {}
static void usage(String label) { <caret>foo(label) }
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object')
  }

  @Test
  void 'vararg vs single with null argument'() {
    def method = resolveTest '''\
static void foo(Object t) {}
static void foo(Object... values) {}
<caret>foo(null)
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.lang.Object')
  }

  @Test
  void 'vararg vs positional 2'() {
    def method = resolveTest '''\
static def foo(Object o) { "obj $o" }
static def foo(Object[] oo) { "arr $oo" }
static usage(Object a) { <caret>foo(a) }
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)
  }

  @Test
  void 'List vs Object array param with null argument'() {
    def method = resolveTest '''\
def foo(List l) {}
def foo(Object[] o) {}
<caret>foo(null)
''', GrMethod
    assert method.parameterList.parameters.first().type.equalsToText('java.util.List')
  }

  @Test
  void 'IDEA-217978'() {
    def method = resolveTest '''\
def <T extends List<Integer>> void foo(T a) {
  a.eve<caret>ry {true}
}
''', GrGdkMethod
    assert method.staticMethod.name == "every"
  }


  @Test
  void 'IDEA-216095'() {
    def method = resolveTest '''\
void foo(Integer i, Class c, Object... objects){
}

void foo(Object i, Class... classes){
}

fo<caret>o(1, Object, Object)
''', GrMethod
    assert method.getParameters().size() == 3
  }

  @Test
  void 'IDEA-216095-2'() {
    def method = resolveTest '''\
void foo(Integer i, Object... objects){
}

void foo(Object i){
}

fo<caret>o(1)
''', GrMethod
    assert method.getParameters().size() == 1
  }

  @Test
  void 'IDEA-216095-3'() {
    def method = resolveTest '''\
void foo(Double i, Object... objects){
}

void foo(Object i, Integer... objects){
}

f<caret>oo(1, 1)
''', GrMethod
    assert method.getParameters()[1].type.canonicalText == "java.lang.Integer..."
  }

  @Test
  void 'IDEA-216095-4'() {
    def method = resolveTest '''\
void foo(Double i, Object... objects){
}

void foo(Object i, Integer... objects){
}

f<caret>oo(1, 1, new Object())
''', GrMethod
    assert method.getParameters()[1].type.canonicalText == "java.lang.Object..."
  }

  @Test
  void 'IDEA-216095-5'() {
    def method = resolveTest '''\
void foo(Runnable... objects){
}

void foo(Object... objects){
}

f<caret>oo(null)
''', GrMethod
    assert method.getParameters()[0].type.canonicalText == "java.lang.Object..."
  }

  @Test
  void 'resolve calls inside closure'() {
    resolveTest '''
def f() {
  def x = 'q'
  1.with {
    print(x.is<caret>Empty())
  }
}''', PsiMethod
  }

  @Test
  void 'resolve calls inside closure with CompileStatic'() {
    resolveTest '''
import groovy.transform.CompileStatic

@CompileStatic
def test() {
    1.with { r -> def x = 1; r.ti<caret>mes { x.byteValue() } }
}''', PsiMethod
  }

  @Test
  void 'resolve calls inside nested closure'() {
    resolveTest '''
def test() {
    1.with { def x = 1; it.times { x.byt<caret>eValue() } }
}''', PsiMethod
  }

}
