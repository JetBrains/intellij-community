// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.util.TestUtils

import static org.jetbrains.plugins.groovy.util.ThrowingTransformation.disableTransformations

class ResolvePropertyTest extends GroovyResolveTestCase {
  final String basePath = TestUtils.testDataPath + "resolve/property/"

  void testParameter1() throws Exception {
    disableTransformations testRootDisposable
    resolve "A.groovy", GrParameter
  }

  void testClosureParameter1() throws Exception {
    disableTransformations testRootDisposable
    resolve "A.groovy", GrParameter
  }

  void testClosureOwner() throws Exception {
    PsiReference ref = configureByFile("closureOwner/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, PsiMethod)
  }

  void testLocal1() throws Exception {
    disableTransformations testRootDisposable
    doTest("local1/A.groovy")
  }

  void testField1() throws Exception {
    disableTransformations testRootDisposable
    doTest("field1/A.groovy")
  }

  void testField2() throws Exception {
    doTest("field2/A.groovy")
  }

  void testArrayLength() throws Exception {
    doTest("arrayLength/A.groovy")
  }

  void testFromGetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/A.groovy")
    assertTrue(ref.resolve() instanceof GrAccessorMethod)
  }

  void testFromGetter2() throws Exception {
    PsiReference ref = configureByFile("fromGetter2/A.groovy")
    assertTrue(ref.resolve() instanceof GrAccessorMethod)
  }

  void testFromSetter2() throws Exception {
    PsiReference ref = configureByFile("fromSetter2/A.groovy")
    assertTrue(ref.resolve() instanceof GrAccessorMethod)
  }

  void testFromSetter() throws Exception {
    PsiReference ref = configureByFile("fromSetter/A.groovy")
    assertTrue(ref.resolve() instanceof GrAccessorMethod)
  }

  void testCatchParameter() throws Exception {
    disableTransformations testRootDisposable
    resolve "CatchParameter.groovy", GrParameter
  }

  void testCaseClause() throws Exception {
    disableTransformations testRootDisposable
    doTest("caseClause/CaseClause.groovy")
  }

  void testGrvy104() throws Exception {
    disableTransformations testRootDisposable
    doTest("grvy104/Test.groovy")
  }

  void testGrvy270() throws Exception {
    PsiReference ref = configureByFile("grvy270/Test.groovy")
    assertNull(ref.resolve())
  }

  void testGrvy1483() throws Exception {
    disableTransformations testRootDisposable
    resolve "Test.groovy", GrVariable
  }

  void testField3() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("field3/A.groovy").element
    GroovyResolveResult resolveResult = ref.advancedResolve()
    assertTrue(resolveResult.element instanceof GrField)
    assertFalse(resolveResult.validResult)
  }

  void testToGetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toGetter/A.groovy").element
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod)resolved))
  }

  void testToSetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement)configureByFile("toSetter/A.groovy").element
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod)resolved))
  }

  void testUndefinedVar1() throws Exception {
    PsiReference ref = configureByFile("undefinedVar1/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, GrBindingVariable)
  }

  void testRecursive1() throws Exception {
    PsiReference ref = configureByFile("recursive1/A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrField)
  }

  void testRecursive2() throws Exception {
    PsiReference ref = configureByFile("recursive2/A.groovy")
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrMethod)
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, ((GrMethod)resolved).returnType.canonicalText)
  }

  void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy")
  }

  void testUndefinedVar3() {
    resolveByText('''
(aa, b) = [1, 4]
c = a<caret>a
''', GrBindingVariable)
  }

  void testDefinedVar1() throws Exception {
    disableTransformations testRootDisposable
    resolve "A.groovy", GrVariable
  }

  void testOperatorOverload() throws Exception {
    doTest("operatorOverload/A.groovy")
  }

  void testEnumConstant() throws Exception {
    PsiReference ref = configureByFile("enumConstant/A.groovy")
    assertTrue(ref.resolve() instanceof GrEnumConstant)
  }

  void testStackOverflow() throws Exception {
    doTest("stackOverflow/A.groovy")
  }

  void testFromDifferentCaseClause() throws Exception {
    PsiReference ref = configureByFile("fromDifferentCaseClause/A.groovy")
    assertNull(ref.resolve())
  }

  void testNotSettingProperty() throws Exception {
    PsiReference ref = configureByFile("notSettingProperty/A.groovy")
    assertNull(ref.resolve())
  }

  void testGrvy633() throws Exception {
    PsiReference ref = configureByFile("grvy633/A.groovy")
    assertNull(ref.resolve())
  }

  void testGrvy575() throws Exception {
    disableTransformations testRootDisposable
    doTest("grvy575/A.groovy")
  }

  void testGrvy747() throws Exception {
    PsiReference ref = configureByFile("grvy747/A.groovy")
    assertTrue(ref.resolve() instanceof GrField)
  }

  void testClosureCall() throws Exception {
    disableTransformations testRootDisposable
    PsiReference ref = configureByFile("closureCall/ClosureCall.groovy")
    assertTrue(ref.resolve() instanceof GrVariable)
  }

  void testUnderscoredField() throws Exception {
    PsiReference ref = configureByFile("underscoredField/UnderscoredField.groovy")
    final GrField field = assertInstanceOf(ref.resolve(), GrField.class)
    assertFalse(ref.isReferenceTo(field.getters[0]))
    assertTrue(ref.isReferenceTo(field))
  }

  void testPropertyWithoutField1() throws Exception {
    PsiReference ref = configureByFile("propertyWithoutField1/PropertyWithoutField1.groovy")
    assertInstanceOf(ref.resolve(), GrMethod.class)
  }

  void testPropertyWithoutField2() throws Exception {
    PsiReference ref = configureByFile("propertyWithoutField2/PropertyWithoutField2.groovy")
    assertInstanceOf(ref.resolve(), GrMethod.class)
  }

  void testFieldAssignedInTheSameMethod() throws Exception {
    PsiReference ref = configureByFile("fieldAssignedInTheSameMethod/FieldAssignedInTheSameMethod.groovy")
    assertInstanceOf(ref.resolve(), GrField.class)
  }

  void testPrivateFieldAssignment() throws Throwable {
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

  void testOverriddenGetter() throws Throwable {
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

  void testIDEADEV40403() {
    myFixture.configureByFile("IDEADEV40403/A.groovy")
    def reference = findReference()
    def resolved = reference.resolve()
    def clazz = assertInstanceOf(resolved, PsiMethod).containingClass
    assertEquals "Script", clazz.name
  }

  void testBooleanGetterPropertyAccess() {
    myFixture.configureByText("a.groovy", "print([].em<caret>pty)")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  def findReference() { myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset) }

  void testTriplePropertyUsages() throws Exception {
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

  void testAliasedStaticImport() throws Exception {
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
    PsiReference ref = configureByFile(fileName)
    PsiElement resolved = ref.resolve()
    assertTrue(resolved instanceof GrVariable)
  }

  private void doUndefinedVarTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName)
    PsiElement resolved = ref.resolve()
    assertInstanceOf(resolved, GrBindingVariable)
  }

  void testBooleanProperty() throws Exception {
    myFixture.configureByText("Abc.groovy", """class A{
    boolean getFoo(){return true}
 boolean isFoo(){return false}
 }
 print new A().f<caret>oo""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertNotNull resolved
    assert ((PsiMethod)resolved).name == "getFoo"
  }

  void testExplicitBooleanProperty() throws Exception {
    myFixture.configureByText("Abc.groovy", """class A{
    boolean foo
 }
 print new A().f<caret>oo""")
    def ref = findReference()
    def resolved = ref.resolve()
    assert ((PsiMethod)resolved).name == "getFoo"
  }

  void testStaticFieldAndNonStaticGetter() {
    myFixture.configureByText("Abc.groovy", "print Float.N<caret>aN")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, PsiField.class
  }

  void testPropertyAndFieldDeclarationInsideClass() {
    myFixture.configureByText("a.groovy", """class Foo {
  def foo
  public def foo

  def bar() {
    print fo<caret>o
  }
}""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrField
    assertTrue((resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  void testPropertyAndFieldDeclarationOutsideClass() {
    myFixture.configureByText("a.groovy", """class Foo {
  def foo
  public def foo

  def bar() {
    print foo
  }
}
print new Foo().fo<caret>o""")
    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  void testPropertyAndFieldDeclarationWithSuperClass1() {
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
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  void testPropertyAndFieldDeclarationWithSuperClass2() {
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
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrField
    assertTrue((resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  void testPropertyAndFieldDeclarationWithSuperClass3() {
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
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrAccessorMethod.class
  }

  void testPropertyAndFieldDeclarationWithSuperClass4() {
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
    def resolved = ref.resolve()
    assertInstanceOf resolved, GrField
    assertTrue(!(resolved as GrField).modifierList.hasExplicitVisibilityModifiers())
  }

  void testReadAccessToStaticallyImportedProperty() {

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

  void testWriteAccessToStaticallyImportedProperty() {
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

  void testGetterToStaticallyImportedProperty() {
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

  void testPropertyInCallExpression() {
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

  void 'test property vs field in call from outside'() {
    def method = resolveByText '''\
class C {
  def foo = { 42 }
  def getFoo() { return { 43 } }
}
new C().<caret>foo(2)
''', GrMethod
    assert !(method instanceof GrAccessorMethod)
  }

  void testPropertyImportedOnDemand() {
    myFixture.addFileToProject("foo/A.groovy", 'package foo; class Foo {static def foo}')
    myFixture.configureByText("B.groovy", """package foo
import static Foo.*
print fo<caret>o""")

    def ref = findReference()
    def resolved = ref.resolve()
    assertInstanceOf(resolved, GrAccessorMethod)
  }

  void testSetterToAliasedImportedProperty() {
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

  void testPhysicalSetterToStaticallyImportedProperty() {
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

  void testPropertyUseInCategory() {
    PsiReference ref = configureByFile("propertyUseInCategory/a.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiMethod
  }

  void testCollectionItemFields() {
    PsiReference ref = configureByFile("collectionItemFields/A.groovy")
    PsiElement resolved = ref.resolve()
    assertInstanceOf resolved, PsiField
  }

  void testFieldAccessInStaticContext() {
    def ref = configureByFile("fieldAccessInStaticContext/A.groovy")
    def resolveResult = (ref as GrReferenceExpression).advancedResolve()
    assertFalse resolveResult.staticsOK
  }

  void testFieldAccessInClosureVsStaticContext() {
    def ref = configureByFile("fieldAccessInClosureVsStaticContext/A.groovy")
    def resolveResult = (ref as GrReferenceExpression).advancedResolve()
    assertTrue resolveResult.staticsOK
  }

  void testUpperCaseFieldAndGetter() {
    assertTrue resolve("A.groovy") instanceof GrField
  }

  void testUpperCaseFieldWithoutGetter() {
    assertInstanceOf(resolve("A.groovy"), GrField)
  }

  void testGetterWithUpperCaseFieldReference() {
    assertNull resolve("A.groovy")
  }

  void testCommandExpressions() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  void testStaticFieldOfInterface() {
    final GroovyResolveResult result = advancedResolve("A.groovy")
    assertTrue result.staticsOK
  }

  void testNonStaticFieldInStaticContext() {
    final GroovyResolveResult result = advancedResolve("A.groovy")
    assertFalse result.staticsOK
  }

  void testPropertyInExprStatement() {
    def result = resolve("A.groovy")
    assertInstanceOf result, GrAccessorMethod
  }

  void testPreferAlias() {
    myFixture.addFileToProject "a/B.groovy", "package a; class B {public static def f1; public static def f2}"
    assertEquals 'f2', ((GrField)resolve("A.groovy")).name
  }

  void testF1property() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  void testAnonymousClassFieldAndLocalVar() {
    disableTransformations testRootDisposable
    final PsiElement resolved = resolve("A.groovy")
    assertInstanceOf resolved, PsiVariable
    assertTrue PsiUtil.isLocalVariable(resolved)
  }

  void _testResolveForVarOutsideOfFor() {
    final PsiElement resolved = resolve("A.groovy")
    assertInstanceOf resolved, GrParameter
  }

  void testDontResolveForVarOutsideOfFor() {
    assertNull resolve("A.groovy")
  }

  void testOperatorOverloading() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  void testResolveClosureOverloader() {
    assertInstanceOf resolve("A.groovy"), GrAccessorMethod
  }

  void testJavaLoggingTransform() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Log class Foo { { lo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'java.util.logging.Logger'
  }

  void testNonLoggingField() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    assert !configureByText("@groovy.util.logging.Log class Foo { { alo<caret>g.inf } }").resolve()
  }

  void testJavaLoggingTransformCustomName() {
    myFixture.addClass('package groovy.util.logging; public @interface Log { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Log('myLog') class Foo { { myLo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'java.util.logging.Logger'
  }

  void testCommonsLoggingTransform() {
    myFixture.addClass('package groovy.util.logging; public @interface Commons { String value() default ""; }')
    def ref = configureByText("@groovy.util.logging.Commons('myLog') class Foo { { myLo<caret>g.inf } }")
    assert assertInstanceOf(ref.resolve(), PsiVariable).type.canonicalText == 'org.apache.commons.logging.Log'
  }

  void testFieldTransform() {
    addGroovyTransformField()
    def ref = configureByText("""@groovy.transform.Field def aaa = 2
def foo() { println <caret>aaa }
""")
    assert ref.resolve() instanceof GrVariable
  }

  void testScriptFieldVariableOutside() {
    myFixture.addFileToProject("Foo.groovy", "if (true) { @groovy.transform.Field def a = 2 }")

    def ref = configureByText("println new Foo().<caret>a")
    assert ref.resolve() instanceof GrVariable
  }

  void testScriptVariableFromScriptMethod() {
    def ref = configureByText("""def aaa = 2
def foo() { println <caret>aaa }
""")
    assert !ref.resolve()
  }

  void testConfigObject() {
    def ref = configureByText('''
def config = new ConfigObject()
print config.config<caret>File''')
    assert ref.resolve()
  }

  void testResolveInsideWith0() {
    def resolved = resolve('a.groovy', GrAccessorMethod)
    assertEquals(resolved.containingClass.name, 'A')
  }

  void testResolveInsideWith1() {
    def resolved = resolve('a.groovy', GrAccessorMethod)
    assertEquals(resolved.containingClass.name, 'B')
  }

  void testLocalVarVsFieldInWithClosure() {
// TODO disableTransformations testRootDisposable
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
    resolveByText '''\
import static pack.Const.getField1

print Fie<caret>ld1
''', null
  }

  void testImportedProperty2() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    resolveByText '''\
import static pack.Const.getField1

print fie<caret>ld1
''', GrAccessorMethod
  }

  void testImportedProperty3() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    resolveByText '''\
import static pack.Const.getField1 as getBar

print Ba<caret>r
''', null
  }

  void testImportedProperty4() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int Field1 = 2
}
''')

    resolveByText '''\
import static pack.Const.getField1 as getBar

print ba<caret>r
''', GrAccessorMethod
  }

  void testImportedProperty5() {
    myFixture.addFileToProject('pack/Const.groovy', '''\
package pack

class Const {
  static final int field1 = 2
}
''')

    resolveByText '''\
import static pack.Const.getField1

print Fie<caret>ld1
''', null
  }

  void testLocalVarVsClassFieldInAnonymous() {
    disableTransformations testRootDisposable
    def resolved = resolveByText '''\
      class A {
        public foo
      }

      def foo = 4

      new A() {
        def foo() {
          print fo<caret>o
        }
      }
''', GrVariable
    assert !(resolved instanceof PsiField)
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
    resolveByText '''\
def foo() {
  abc = 4
}

def bar() {
  print ab<caret>c
}
''', null
  }

  void testResolveBinding6() {
    resolveByText'''\
def foo() {
  print ab<caret>c
}

def bar() {
  abc = 4
}
''', null
  }

  void testResolveBinding7() {
    resolveByText '''\
def foo() {
  a<caret>bc = 4
}

def bar() {
  print abc
}
''', null
  }

  void testResolveBinding8() {
    resolveByText '''\
def foo() {
  print abc
}

def bar() {
  a<caret>bc = 4
}
''', null
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
    resolveByText '''\
def foo() {
  aa<caret>a
}

aaa = 1
''', null
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
    disableTransformations testRootDisposable

    resolveByText '''\
      def p = [A:5]

      print <caret>p
''', GrVariable
  }

  void testVarVsPackage3() {
    myFixture.addClass('''package p; public class A {}''')
    disableTransformations testRootDisposable

    resolveByText '''\
      def p = [A:{2}]

      print <caret>p.A()
''', GrVariable
  }

  void testVarVsPackage4() {
    myFixture.addClass('''package p; public class A {public static int foo(){return 2;}}''')

    resolveByText('''\
      def p = [A:[foo:{-2}]]

      print <caret>p.A.foo()
''', PsiPackage)
  }

  void testVarVsClass1() {
    myFixture.addClass('package p; public class A {public static int foo() {return 1;}}')
    disableTransformations testRootDisposable

    resolveByText '''\
import p.A

def A = [a:{-1}]

print <caret>A
''', GrVariable
  }

  void testVarVsClass2() {
    myFixture.addClass('package p; public class A {public static int foo() {return 1;}}')
    disableTransformations testRootDisposable

    resolveByText '''\
import p.A

def A = [a:{-1}]

print <caret>A.a()
''', GrVariable
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

  void 'test on demand static import'() {
    fixture.with {
      addClass '''
package somepackage;
public class Holder {
  public static Object getStuff() { return new Object(); }
}
'''
      def method = resolveByText('''\
import static somepackage.Holder.*
class Foo {
  static field = stu<caret>ff
}
''', PsiMethod)
      assert method.name == 'getStuff'
    }
  }

  void 'test prefer local over map key'() {
    disableTransformations testRootDisposable
    resolveByText 'def abc = 42; [:].with { <caret>abc }', GrVariable
  }

  void 'test Class property vs instance property'() {
    def property = resolveByText '''\
class A {
  int getName() { 42 }
}

println A.<caret>name
''', PsiMethod
    assert property.containingClass.qualifiedName == 'java.lang.Class'
  }

  void "test don't resolve to field in method call"() {
    resolveByText '''\
class Fff {
  List<String> testThis = [""]
  def usage() { <caret>testThis(1) }
  def testThis(a) {}
}
''', GrMethod
  }

  void 'test static field via class instance'() {
    resolveByText '''\
class A { public static someStaticField = 42 }
def a = A // class instance
a.<caret>someStaticField
''', GrField
  }

  void 'test gpath in array'() {
    resolveByText """
class A {
    int foo
    A(int x) { foo = x }
}

A[] ai = [new A(1), new A(2)].toArray()

println(ai.fo<caret>o)
""", GrAccessorMethod
  }

  void 'test gpath in array 2'() {
    resolveByText """
class A {
    int length
    A(int x) { length = x }
}

A[] ai = [new A(1), new A(2)].toArray()

println(ai.len<caret>gth)
""", GrField
  }
}
