/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author ven
 */
public class ResolvePropertyTest extends GroovyResolveTestCase {
  final String basePath = TestUtils.testDataPath + "resolve/property/"

  public void testParameter1() throws Exception {
    doTest("parameter1/A.groovy");
  }

  public void testClosureParameter1() throws Exception {
    doTest("closureParameter1/A.groovy");
  }

  public void testClosureOwner() throws Exception {
    PsiReference ref = configureByFile("closureOwner/A.groovy");
    PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, PsiVariable);
    assertEquals((resolved as PsiVariable).type.canonicalText, "W");
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

  public void testArrayLength() throws Exception {
    doTest("arrayLength/A.groovy");
  }

  public void testFromGetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
    assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromGetter2() throws Exception {
    PsiReference ref = configureByFile("fromGetter2/A.groovy");
    assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromSetter2() throws Exception {
    PsiReference ref = configureByFile("fromSetter2/A.groovy");
    assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void testFromSetter() throws Exception {
    PsiReference ref = configureByFile("fromSetter/A.groovy");
    assertTrue(ref.resolve() instanceof GrAccessorMethod);
  }

  public void _testForVariable2() throws Exception {
    doTest("forVariable2/ForVariable.groovy");
  }

  public void testCatchParameter() throws Exception {
    doTest("catchParameter/CatchParameter.groovy");
  }

  public void testCaseClause() throws Exception {
    doTest("caseClause/CaseClause.groovy");
  }

  public void testGrvy104() throws Exception {
    doTest("grvy104/Test.groovy");
  }

  public void testGrvy270() throws Exception {
    PsiReference ref = configureByFile("grvy270/Test.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy1483() throws Exception {
    PsiReference ref = configureByFile("grvy1483/Test.groovy");
    assertNotNull(ref.resolve());
  }

  public void testField3() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("field3/A.groovy").element;
    GroovyResolveResult resolveResult = ref.advancedResolve();
    assertTrue(resolveResult.element instanceof GrField);
    assertFalse(resolveResult.validResult);
  }

  public void testToGetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toGetter/A.groovy").element;
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod)resolved));
  }

  public void testToSetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toSetter/A.groovy").element;
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod)resolved));
  }

  public void testUndefinedVar1() throws Exception {
    PsiReference ref = configureByFile("undefinedVar1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, GrBindingVariable);
  }

  public void testRecursive1() throws Exception {
    PsiReference ref = configureByFile("recursive1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrField);
  }

  public void testRecursive2() throws Exception {
    PsiReference ref = configureByFile("recursive2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((GrMethod)resolved).returnType.canonicalText);
  }

  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy");
  }

  public void testUndefinedVar3() {
    resolveByText('''
(aa, b) = [1, 4]
c = a<caret>a
''', GrBindingVariable)
  }

  public void testDefinedVar1() throws Exception {
    doTest("definedVar1/A.groovy");
  }

  public void testOperatorOverload() throws Exception {
    doTest("operatorOverload/A.groovy");
  }

  public void testEnumConstant() throws Exception {
    PsiReference ref = configureByFile("enumConstant/A.groovy");
    assertTrue(ref.resolve() instanceof GrEnumConstant);
  }

  public void testStackOverflow() throws Exception {
    doTest("stackOverflow/A.groovy");
  }

  public void testFromDifferentCaseClause() throws Exception {
    PsiReference ref = configureByFile("fromDifferentCaseClause/A.groovy");
    assertNull(ref.resolve());
  }

  public void testNotSettingProperty() throws Exception {
    PsiReference ref = configureByFile("notSettingProperty/A.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy633() throws Exception {
    PsiReference ref = configureByFile("grvy633/A.groovy");
    assertNull(ref.resolve());
  }

  public void testGrvy575() throws Exception {
    doTest("grvy575/A.groovy");
  }

  public void testGrvy747() throws Exception {
    PsiReference ref = configureByFile("grvy747/A.groovy");
    assertTrue(ref.resolve() instanceof GrField);
  }

  public void testClosureCall() throws Exception {
    PsiReference ref = configureByFile("closureCall/ClosureCall.groovy");
    assertTrue(ref.resolve() instanceof GrVariable);
  }

  public void testUnderscoredField() throws Exception {
    PsiReference ref = configureByFile("underscoredField/UnderscoredField.groovy");
    final GrField field = assertInstanceOf(ref.resolve(), GrField.class);
    assertFalse(ref.isReferenceTo(field.getters[0]));
    assertTrue(ref.isReferenceTo(field));
  }

  public void testPropertyWithoutField1() throws Exception {
    PsiReference ref = configureByFile("propertyWithoutField1/PropertyWithoutField1.groovy");
    assertInstanceOf(ref.resolve(), GrMethod.class);
  }

  public void testPropertyWithoutField2() throws Exception {
    PsiReference ref = configureByFile("propertyWithoutField2/PropertyWithoutField2.groovy");
    assertInstanceOf(ref.resolve(), GrMethod.class);
  }

  public void testFieldAssignedInTheSameMethod() throws Exception {
    PsiReference ref = configureByFile("fieldAssignedInTheSameMethod/FieldAssignedInTheSameMethod.groovy");
    assertInstanceOf(ref.resolve(), GrField.class);
  }

  public void testPrivateFieldAssignment() throws Throwable {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
                          class Aaaaa {
                            final def aaa

                            def foo() {
                              a<caret>aa = 2
                            }
                          }""")
    def reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertInstanceOf(reference.resolve(), GrField.class)
  }

  public void testOverriddenGetter() throws Throwable {
    myFixture.configureByText("a.groovy", """interface Foo {
                                              def getFoo()
                                            }
                                            interface Bar extends Foo {
                                              def getFoo()
                                            }

                                            Bar b
                                            b.fo<caret>o""")
    def reference = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertEquals("Bar", assertInstanceOf(reference.resolve(), GrMethod.class).containingClass.name)
  }

  public void testIDEADEV40403() {
    myFixture.configureByFile("IDEADEV40403/A.groovy");
    def reference = findReference()
    def resolved = reference.resolve()
    def clazz = assertInstanceOf(resolved, PsiMethod).containingClass
    assertEquals "Script", clazz.name
  }

  public void testBooleanGetterPropertyAccess() {
    myFixture.configureByText("a.groovy", "print([].em<caret>pty)");
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  def findReference() { myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) }

  public void testTriplePropertyUsages() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo {
  def bar
  def zoo = <caret>bar
}
"""
    def ref = findReference()
    GrField target = assertInstanceOf(ref.resolve(), GrField)
    assertTrue ref.isReferenceTo(target)
    assertFalse ref.isReferenceTo(target.getters[0])
    assertFalse ref.isReferenceTo(target.setter)
  }

  public void testAliasedStaticImport() throws Exception {
    myFixture.addClass """ class Main {
  static def foo=4
"""

    myFixture.configureByText "a.groovy", """
import static Main.foo as bar
print ba<caret>r
}
"""
    def ref = findReference()
    def target = assertInstanceOf(ref.resolve(), PsiField)
    assertEquals target.name, "foo"
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrVariable);
  }

  private void doUndefinedVarTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertInstanceOf(resolved, GrBindingVariable);
  }

  public void testBooleanProperty() throws Exception {
    myFixture.configureByText("Abc.groovy", """class A{
    boolean getFoo(){return true}
 boolean isFoo(){return false}
 }
 print new A().f<caret>oo""");
    def ref = findReference()
    def resolved = ref.resolve();
    assertNotNull resolved
    assert ((PsiMethod)resolved).name == "getFoo"
  }

  public void testExplicitBooleanProperty() throws Exception {
    myFixture.configureByText("Abc.groovy", """class A{
    boolean foo
 }
 print new A().f<caret>oo""");
    def ref = findReference()
    def resolved = ref.resolve();
    assert ((PsiMethod)resolved).name == "getFoo"
  }

  public void testStaticFieldAndNonStaticGetter() {
    myFixture.configureByText("Abc.groovy", "print Float.N<caret>aN")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiField.class
  }

  public void testPropertyAndFieldDeclarationInsideClass() {
    myFixture.configureByText("a.groovy", """class Foo {
  def foo
  public def foo

  def bar() {
    print fo<caret>o
  }
}""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrField
    assertTrue((resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  public void testPropertyAndFieldDeclarationOutsideClass() {
    myFixture.configureByText("a.groovy", """class Foo {
  def foo
  public def foo

  def bar() {
    print foo
  }
}
print new Foo().fo<caret>o""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  public void testPropertyAndFieldDeclarationWithSuperClass1() {
    myFixture.configureByText("a.groovy", """
class Bar{
  def foo
}
class Foo extends Bar {
  public def foo

  def bar() {
    print foo
  }
}
print new Foo().fo<caret>o""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  public void testPropertyAndFieldDeclarationWithSuperClass2() {
    myFixture.configureByText("a.groovy", """
class Bar{
  def foo
}
class Foo extends Bar {
  public def foo

  def bar() {
    print f<caret>oo
  }
}
print new Foo().foo""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrField
    assertTrue((resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  public void testPropertyAndFieldDeclarationWithSuperClass3() {
    myFixture.configureByText("a.groovy", """
class Bar{
  public def foo
}
class Foo extends Bar {
  def foo

  def bar() {
    print foo
  }
}
print new Foo().fo<caret>o""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  public void testPropertyAndFieldDeclarationWithSuperClass4() {
    myFixture.configureByText("a.groovy", """
class Bar{
  public def foo
}
class Foo extends Bar {
  def foo

  def bar() {
    print f<caret>oo
  }
}
print new Foo().foo""")
    def ref = findReference()
    def resolved = ref.resolve();
    assertInstanceOf resolved, GrField
    assertTrue(!(resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  public void testReadAccessToStaticallyImportedProperty() {

    myFixture.addFileToProject("a.groovy", """class Foo {
  static def bar
}""")
    myFixture.configureByText("b.groovy", """import static Foo.bar
print ba<caret>r
""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  public void testWriteAccessToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """class Foo {
  static def bar
}""")
    myFixture.configureByText("b.groovy", """import static Foo.bar
ba<caret>r = 2
""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  public void testGetterToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """class Foo {
  static def bar
}""")
    myFixture.configureByText("b.groovy", """import static Foo.bar
set<caret>Bar(2)
""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod
  }

  public void testPropertyInCallExpression() {
    myFixture.configureByText("a.groovy", """
class Foo {
  def foo = {
    return {int i -> print i}
  }

  def foo(String s){
    print s
  }
}
new Foo().fo<caret>o(2)"""
    )
    def ref = findReference()
    def resolved = ref.resolve()

    assertInstanceOf resolved, GrAccessorMethod
  }

  public void testPropertyImportedOnDemand() {
    myFixture.addFileToProject("foo/A.groovy", 'package foo; class Foo {static def foo}')
    myFixture.configureByText("B.groovy", """package foo
import static Foo.*
print fo<caret>o""")

    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrAccessorMethod)
  }

  public void testFieldAccessOutsideClass() {
    myFixture.configureByText("A.groovy", """
class X {
  public def foo = 3
  def getFoo() {2}
  def setFoo(def foo) {}
}

print new X().@f<caret>oo
""")

    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrField)
  }

  public void testSetterToAliasedImportedProperty() {
    myFixture.addFileToProject("a.groovy", """class Foo {
  static def bar
}""")
    myFixture.configureByText("b.groovy", """import static Foo.bar as foo
set<caret>Foo(2)
""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod
  }

  public void testPhysicalSetterToStaticallyImportedProperty() {
    myFixture.addFileToProject("a.groovy", """class Foo {
  static def bar
  static def setBar(def bar){}
}""")
    myFixture.configureByText("b.groovy", """import static Foo.bar as foo
set<caret>Foo(2)
""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrMethod
  }

  public void testPropertyUseInCategory() {
    PsiReference ref = configureByFile("propertyUseInCategory/a.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  public void testCollectionItemFields() {
    PsiReference ref = configureByFile("collectionItemFields/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiField
  }

  public void testFieldAccessInStaticContext() {
    def ref = configureByFile("fieldAccessInStaticContext/A.groovy")
    def resolveResult = (ref as GrReferenceExpression).advancedResolve()
    assertFalse resolveResult.staticsOK
  }

  public void testFieldAccessInClosureVsStaticContext() {
    def ref = configureByFile("fieldAccessInClosureVsStaticContext/A.groovy")
    def resolveResult = (ref as GrReferenceExpression).advancedResolve()
    assertTrue resolveResult.staticsOK
  }

  public void testUpperCaseFieldAndGetter() {
    assertTrue resolve("A.groovy") instanceof GrField
  }

  public void testUpperCaseFieldWithoutGetter() {
    assertInstanceOf(resolve("A.groovy"), GrAccessorMethod)
  }

  public void testGetterWithUpperCaseFieldReference() {
    assertNull resolve("A.groovy")
  }

  public void testCommandExpressions() {
    assertInstanceOf resolve("A.groovy"), GrField
  }

  public void testMetaClassIsNotResolvedWithMapQualifier() {
    assertNull resolve("A.groovy")
  }

  public void testMetaClassIsResolvesWithMapQualifier() {
    assertInstanceOf resolve("A.groovy"), PsiMethod
  }

  public void testStaticFieldOfInterface() {
    final GroovyResolveResult result = advancedResolve("A.groovy")
    assertTrue result.staticsOK
  }

  public void testNonStaticFieldInStaticContext() {
    final GroovyResolveResult result = advancedResolve("A.groovy")
    assertFalse result.staticsOK
  }

  public void testPropertyInExprStatement() {
    def result = resolve("A.groovy")
    assertInstanceOf result, GrAccessorMethod
  }

  public void testPreferAlias() {
    myFixture.addFileToProject "a/B.groovy", "package a; class B {public static def f1; public static def f2}"
    assertEquals 'f2', ((GrField)resolve("A.groovy")).name
  }

  public void testF1property() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  public void testAnonymousClassFieldAndLocalVar() {
    final PsiElement resolved = resolve("A.groovy")
    assertInstanceOf resolved, PsiVariable
    assertTrue PsiUtil.isLocalVariable(resolved)
  }

  public void _testResolveForVarOutsideOfFor() {
    final PsiElement resolved = resolve("A.groovy")
    assertInstanceOf resolved, GrParameter
  }

  public void testDontResolveForVarOutsideOfFor() {
    assertNull resolve("A.groovy")
  }

  public void testOperatorOverloading() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  public void testResolveClosureOverloader() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  public void testJavaLoggingTransform() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Log class Foo { { lo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'java.util.logging.Logger'
  }

  public void testNonLoggingField() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    assert !configureByText("@groovy.util.logging.Log class Foo { { alo<caret>g.inf } }").resolve()
  }

  public void testJavaLoggingTransformCustomName() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Log('myLog') class Foo { { myLo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'java.util.logging.Logger'
  }

  public void testCommonsLoggingTransform() {
    myFixture.addClass('package groovy.util.logging; public @interface Commons { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Commons('myLog') class Foo { { myLo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'org.apache.commons.logging.Log'
  }

  public void testFieldTransform() {
    addGroovyTransformField()
    def ref = configureByText("""@groovy.transform.Field def aaa = 2
def foo() { println <caret>aaa }
""")
    assert ref.resolve() instanceof GrVariable
  }

  public void testScriptFieldVariableOutside() {
    myFixture.addFileToProject("Foo.groovy", "if (true) { @groovy.transform.Field def a = 2 }")

    def ref = configureByText("println new Foo().<caret>a")
    assert ref.resolve() instanceof GrVariable
  }

  public void testScriptVariableFromScriptMethod() {
    def ref = configureByText("""def aaa = 2
def foo() { println <caret>aaa }
""")
    assert !ref.resolve()
  }

  public void testConfigObject() {
    def ref = configureByText('''
def config = new ConfigObject()
print config.config<caret>File''')
    assert ref.resolve()
  }

  public void testDontResolvePropertyInMap() {
    def ref = configureByText('''
def map = new HashMap()
print map.cla<caret>ss''')

    assert !ref.resolve()
  }

  void 'test custom map properties'() {
    myFixture.addFileToProject 'classes.groovy', '''\
class Pojo {
    def pojoProperty
    def anotherPojoProperty
}

class SomeMapClass extends HashMap<String, Pojo> {

    public static final CONSTANT = 1

    static class Inner {
        public static final INNER_CONSTANT = 4
    }
}
'''
    (configureByText('SomeMapClass.CONST<caret>ANT').element as GrReferenceExpression).with { ref ->
      assert ref.resolve() instanceof GrField
      assert ref.type.equalsToText('java.lang.Integer')
    }
    (configureByText('SomeMapClass.Inn<caret>er').element as GrReferenceExpression).with { ref ->
      assert ref.resolve() instanceof GrClassDefinition
      assert ref.type.equalsToText('java.lang.Class<SomeMapClass.Inner>')
    }
    assert configureByText('SomeMapClass.Inner.INNER_<caret>CONSTANT').resolve() instanceof GrField

    assert !configureByText('def m = new SomeMapClass(); m.CONS<caret>TANT').resolve()
    assert !configureByText('def m = new SomeMapClass(); m.In<caret>ner').resolve()

    configureByText('def m = new SomeMapClass(); m.CONSTANT.pojo<caret>Property').resolve().with { resolved ->
      assert resolved instanceof GrAccessorMethod
      assert resolved.containingClass.name == 'Pojo'
    }
    configureByText('def m = new SomeMapClass(); m.Inner.anotherPojo<caret>Property').resolve().with { resolved ->
      assert resolved instanceof GrAccessorMethod
      assert resolved.containingClass.name == 'Pojo'
    }
    configureByText('def <T extends SomeMapClass> void foo(T a) { a.CONS<caret>TANT }').with { ref ->
      assert !ref.resolve()
      assert (ref as GrReferenceExpression).type.equalsToText('Pojo')
    }
  }

  public void testResolveInsideWith0() {
    def resolved = resolve('a.groovy', GrAccessorMethod)
    assertEquals(resolved.containingClass.name, 'A')
  }

  public void testResolveInsideWith1() {
    def resolved = resolve('a.groovy', GrField)
    assertEquals(resolved.containingClass.name, 'B')
  }


  void testLocalVarVsFieldInWithClosure() {
    def ref = configureByText('''\
class Test {
  def var
}

int var = 4
new Test().with() {
  print v<caret>ar
}
''')
    assertFalse ref.resolve() instanceof GrField
    assertTrue ref.resolve() instanceof GrVariable
  }

  void testCapitalizedProperty1() {
    def ref = configureByText('''\
class A {
  def Prop
}

new A().Pro<caret>p''')
    assertNotNull ref.resolve()
  }

  void testCapitalizedProperty2() {
    def ref = configureByText('''\
class A {
  def Prop
}

new A().pro<caret>p''')
    assertNotNull ref.resolve()
  }

  void testCapitalizedProperty3() {
    def ref = configureByText('''\
class A {
  def prop
}

new A().Pro<caret>p''')
    assertNull ref.resolve()
  }

  void testCapitalizedProperty4() {
    def ref = configureByText('''\
class A {
  def prop
}

new A().pro<caret>p''')
    assertNotNull ref.resolve()
  }

  void testGetChars() {
    def ref = configureByText('''\
'abc'.cha<caret>rs
''')
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrGdkMethod)
    def method = resolved.staticMethod as PsiMethod

    assertEquals(method.parameterList.parameters[0].type.canonicalText, 'java.lang.CharSequence')
  }

  void testLocalVarNotAvailableInClass() {
    def ref = configureByText('''\
def aa = 5

class Inner {
  def foo() {
    print a<caret>a
  }
}''')
    assertNull(ref.resolve())
  }

  void testLocalVarNotAvailableInMethod() {
    def ref = configureByText('''\
def aa = 5

def foo() {
  print a<caret>a
}''')
    assertNull(ref.resolve())
  }

  void testScriptFieldNotAvailableInClass() {
    addGroovyTransformField()
    def ref = configureByText('''\
import groovy.transform.Field

@Field
def aa = 5

class X {
  def foo() {
    print a<caret>a
  }
}''')
    assertNull(ref.resolve())
  }

  void testInitializerOfScriptField() {
    addGroovyTransformField()
    def ref = configureByText('''\
import groovy.transform.Field

def xx = 5
@Field
def aa = 5 + x<caret>x
''')
    assertNull(ref.resolve())
  }

  void testInitializerOfScriptField2() {
    addGroovyTransformField()
    def ref = configureByText('''\
import groovy.transform.Field

@Field
def xx = 5

@Field
def aa = 5 + x<caret>x
''')
    assertInstanceOf(ref.resolve(), GrVariable)
  }

  void testAliasedImportedPropertyWithGetterInAlias() {
    myFixture.addFileToProject('Foo.groovy', '''\
class Foo {
  static def prop = 2
}
''')

    def ref = configureByText('''\
import static Foo.getProp as getOther

print othe<caret>r
''')

    assertInstanceOf(ref.resolve(), GrAccessorMethod)
  }

  void testPrefLocalVarToScriptName() {
    myFixture.addFileToProject('foo.groovy', 'print 2')

    def ref = configureByText('''\
class Bar {
    public float FOO = 1.23f
    public float ZZZ = 5.78f
    public float pi = 3.14f
    public float xxx = 6f

    void doSmth() {}
}

class User {
    public static void main(String[] args) {
        Bar foo = new Bar()
        foo.bar()
        println foo.F<caret>OO * foo.ZZZ * foo.pi * foo.xxx
        foo.doSmth()
    }
}
''')

    assertInstanceOf(ref.resolve(), GrField)
  }

  void testResolveEnumConstantInsideItsInitializer() {
    def ref = configureByText('''\
enum MyEnum {
    CONST {
        void get() {
            C<caret>ONST
        }
    }

}
''')
    assertNotNull(ref)
  }

  void testSpreadWithIterator() {
    final PsiReference ref = configureByText('''\
class Person { String name }
class Twin {
    Person one, two
    def iterator() {
        return [one, two].iterator()
    }
}

def tw = new Twin(one: new Person(name:'Tom'),
                  two: new Person(name:'Tim'))
assert tw*.nam<caret>e == ['Tom', 'Tim']
''')

    final resolved = assertInstanceOf(ref.resolve(), PsiMember)
    assertNotNull(resolved.containingClass)
    assertEquals('Person', resolved.containingClass.name)
  }

  void testAutoSpread() {
    def ref = configureByText('''\
class A {
    String getString() {return "a";}
}

println ([[new A()]].stri<caret>ng)
''')

    final resolved = ref.resolve()

    assertNotNull(resolved)
  }

  void testImportedProperty() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    final ref = configureByText('''\
import static pack.Const.getField1

print Fie<caret>ld1
''')

    assertNotNull(ref.resolve())
  }

  void testImportedProperty2() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    final ref = configureByText('''\
import static pack.Const.getField1

print fie<caret>ld1
''')

    assertNotNull(ref.resolve())
  }

  void testImportedProperty3() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    final ref = configureByText('''\
import static pack.Const.getField1 as getBar

print Ba<caret>r
''')

    assertNotNull(ref.resolve())
  }

  void testImportedProperty4() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    final ref = configureByText('''\
import static pack.Const.getField1 as getBar

print ba<caret>r
''')

    assertNotNull(ref.resolve())
  }

  void testImportedProperty5() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int field1 = 2
}
''')

    final ref = configureByText('''\
import static pack.Const.getField1

print Fie<caret>ld1
''')

    assertNotNull(ref.resolve())
  }

  void testLocalVarVsClassFieldInAnonymous() {
    final ref = configureByText('a.groovy', '''\
      class A {
        public foo
      }

      def foo = 4

      new A() {
        def foo() {
          print fo<caret>o
        }
      }
''')

    assertFalse(ref.resolve() instanceof PsiField)
    assertTrue(ref.resolve() instanceof GrVariable)
  }

  void testInterfaceDoesNotResolveWithExpressionQualifier() {
    def ref = configureByText('''\
class Foo {
  interface Inner {
  }

  public Inner = 5
}

new Foo().Inn<caret>er
''')

    assertInstanceOf(ref.resolve(), PsiField)
  }

  void testInterfaceDoesNotResolveWithExpressionQualifier2() {
    def ref = configureByText('''\
class Foo {
  interface Inner {
  }

  public Inner = 5
}

def foo = new Foo()
print foo.Inn<caret>er
''')

    assertInstanceOf(ref.resolve(), PsiField)
  }

  void testResolveBinding1() {
    resolveByText('''\
abc = 4

print ab<caret>c
''', GrBindingVariable)
  }

  void ignoreTestResolveBinding2() {
    resolveByText('''\
print ab<caret>c

abc = 4
''', GrBindingVariable)
  }

  void testResolveBinding3() {
    resolveByText('''\
a<caret>bc = 4

print abc
''', GrBindingVariable)
  }

  void testResolveBinding4() {
    resolveByText('''\
print abc

a<caret>bc = 4
''', GrBindingVariable)
  }


  void testResolveBinding5() {
    assert resolveByText('''\
def foo() {
  abc = 4
}

def bar() {
  print ab<caret>c
}
''') == null
  }

  void testResolveBinding6() {
    assert resolveByText('''\
def foo() {
  print ab<caret>c
}

def bar() {
  abc = 4
}
''') == null
  }

  void testResolveBinding7() {
    assert resolveByText('''\
def foo() {
  a<caret>bc = 4
}

def bar() {
  print abc
}
''') == null
  }

  void testResolveBinding8() {
    assert resolveByText('''\
def foo() {
  print abc
}

def bar() {
  a<caret>bc = 4
}
''') == null
  }

  void testBinding9() {
    resolveByText('''\
a<caret>a = 5
print aa
aa = 6
print aa
''', GrBindingVariable)
  }

  void testBinding10() {
    resolveByText('''\
aa = 5
print a<caret>a
aa = 6
print aa
''', GrBindingVariable)
  }

  void testBinding11() {
    resolveByText('''\
aa = 5
print aa
a<caret>a = 6
print aa
''', GrBindingVariable)
  }

  void testBinding12() {
    resolveByText('''\
aa = 5
print aa
aa = 6
print a<caret>a
''', GrBindingVariable)
  }

  void testBinding13() {
    resolveByText '''\
aaa = 1

def foo() {
  aa<caret>a
}
''', GrBindingVariable
  }

  void testBinding14() {
    assert resolveByText('''\
def foo() {
  aa<caret>a
}

aaa = 1
''') == null
  }

  void testVarVsPackage1() {
    myFixture.addClass('''package p; public class A {}''')

    resolveByText('''\
      def p = [A:5]

      print <caret>p.A
''', PsiPackage)
  }

  void testVarVsPackage2() {
    myFixture.addClass('''package p; public class A {}''')

    resolveByText('''\
      def p = [A:5]

      print <caret>p
''', PsiVariable)
  }

  void testVarVsPackage3() {
    myFixture.addClass('''package p; public class A {}''')

    resolveByText('''\
      def p = [A:{2}]

      print <caret>p.A()
''', PsiVariable)
  }

  void testVarVsPackage4() {
    myFixture.addClass('''package p; public class A {public static int foo(){return 2;}}''')

    resolveByText('''\
      def p = [A:[foo:{-2}]]

      print <caret>p.A.foo()
''', PsiVariable)
  }

  void testVarVsClass1() {
    myFixture.addClass('package p; public class A {public static int foo() {return 1;}}')

    resolveByText('''\
import p.A

def A = [a:{-1}]

print <caret>A
''', PsiVariable)
  }

  void testVarVsClass2() {
    myFixture.addClass('package p; public class A {public static int foo() {return 1;}}')

    resolveByText('''\
import p.A

def A = [a:{-1}]

print <caret>A.a()
''', PsiVariable)
  }

  void testPropertyVsAccessor() {
    resolveByText('''\
class ProductServiceImplTest  {
    BackendClient backendClient

    def setup() {
        new ProductServiceImpl() {
            protected BackendClient getBackendClient() {
                return backend<caret>Client // <--- this expression is highlighted as member variable
            }
        }
    }
}

class BackendClient{}
class ProductServiceImpl{}
''', GrMethod)
  }

  void testPropertyVsAccessor2() {
    resolveByText('''\
class ProductServiceImplTest  {
    def setup() {
        new ProductServiceImpl() {
            BackendClient backendClient

            protected BackendClient getBackendClient() {
                return backendC<caret>lient
            }
        }
    }
}

class BackendClient{}
class ProductServiceImpl{}
''', GrField)
  }

  void testPropertyVsAccessor3() {
    resolveByText('''\
class ProductServiceImplTest  {
    BackendClient backendClient

    protected BackendClient getBackendClient() {
        return backendClient
    }
    def setup() {
        new ProductServiceImpl() {
           def foo() {
             return backendClie<caret>nt
           }
        }
    }
}

class BackendClient{}
class ProductServiceImpl{}
''', GrMethod)
  }

  void testTraitPublicField1() {
    resolveByText('''
trait T {
  public int field = 4
}

class C implements T {

  void foo() {
    print T__fie<caret>ld
  }
}
''', GrField)
  }

  void testTraitPublicField2() {
    resolveByText('''
trait T {
  public int field = 4

  void foo() {
    print fiel<caret>d
  }
}
''', GrField)
  }

  void testTraitPublicField3() {
    resolveByText('''
trait T {
  public int field = 4

  void foo() {
    print T__fie<caret>ld
  }
}
''', GrField)
  }

  void testTraitPublicField4() {
    resolveByText('''
trait T {
  public int field = 4
}

class C implements T {}

new C().T__fiel<caret>d
''', GrField)
  }

  void testTraitProperty1() {
    resolveByText('''
trait T {
  int prop = 4
}

class C extends T {}

new C().pr<caret>op
''', GrTraitMethod)
  }

  void testTraitProperty2() {
    resolveByText('''
trait T {
  int prop = 4
}

class C extends T {
  def bar() {
    print pro<caret>p
  }
}
''', GrTraitMethod)
  }

  void testTraitProperty3() {
    resolveByText('''
trait T {
  int prop = 4

  void foo() {
    print pro<caret>p
  }
}
''', GrField)
  }

  void testTraitPropertyFromAsOperator1() {
    resolveByText('''
trait A {
  def foo = 5
}
class B {
  def bar() {}
}

def v = new B() as A
print v.fo<caret>o
''', GrAccessorMethod)
  }

  void testTraitPropertyFromAsOperator2() {
    resolveByText('''
trait A {
  public foo = 5
}
class B {
  def bar() {}
}

def v = new B() as A
print v.A<caret>__foo
''', GrField)
  }

  void testTraitField1() {
    resolveByText('''
      trait T {
        public foo = 4
      }

      class X implements T{
        def bar() {
          print fo<caret>o
        }
      }
''', null)
  }

  void testTraitField2() {
    resolveByText('''
      trait T {
        public foo

        def bar() {
          print fo<caret>o
        }
      }
''', PsiField)
  }

  void 'test setter overloading'() {
    def method = resolveByText('''\
class A {
    void setFoo(File f) {}

    void setFoo(String s) {}
}

new A().fo<caret>o = ""
''', GrMethod)
    assert method.parameterList.parameters.first().type.canonicalText == 'java.lang.String'
  }
}
