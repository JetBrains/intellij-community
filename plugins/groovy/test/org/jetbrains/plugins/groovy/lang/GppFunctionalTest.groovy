/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.*
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import junit.framework.ComparisonFailure
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInspection.GroovyUnusedDeclarationInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
class GppFunctionalTest extends LightCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GppProjectDescriptor.instance;
  }

  protected void setUp() {
    super.setUp()
  }

  public void testCastListToIterable() throws Exception {
    myFixture.addClass("class X extends java.util.ArrayList<Integer> {}")
    testAssignability """
X ints = [239, 4.2d]
"""
  }

  public void testCastListToAnything() throws Exception {
    testAssignability """
File f1 = ['path']
File f2 = <warning descr="Constructor 'File' in 'java.io.File' cannot be applied to '(java.lang.String, java.lang.Integer, java.lang.Boolean, java.lang.Integer)'">['path', 2, true, 42]</warning>
"""
  }

  public void testCastMapToAnotherMap() throws Exception {
    myFixture.addClass """
public class Y extends java.util.HashMap<String, String> {
  public Y(int initialCapacity) {
    super(initialCapacity);
  }
}
"""

    testAssignability """
HashMap<String, File> m1 = ['a':['b']]
Y y = <warning descr="Constructor 'Y' in 'Y' cannot be applied to '(['a':java.lang.String])'">[a:'b']</warning>
"""
  }

  public void testAnonymousClass() throws Exception {
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    testAssignability """
def x = new Object() {
  def foo() {
    HashMap<String, File> m1 = ['a':['b']]
    HashMap<String, File> <warning descr="Cannot assign 'File' to 'HashMap<String, File>'">m2</warning> = new File('aaa')
  }
}
"""
  }

  public void testCastMapToObject() throws Exception {
    myFixture.addClass("class Foo { String name; void foo() {} }")
    testAssignability """
Foo f = [name: 'aaa', foo: { println 'hi' }, anotherProperty: 42 ]
"""
  }

  void testAssignability(String text) {
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    PsiFile file = configureGppScript(text)
    myFixture.testHighlighting(true, false, false, file.virtualFile)
  }

  private PsiFile configureScript(String text) {
    return myFixture.configureByText("a.groovy", text)
  }
  private PsiFile configureGppScript(String text) {
    return myFixture.configureByText("a.gpp", text)
  }

  public void testDeclaredVariableTypeIsMoreImportantThanTheInitializerOne() throws Exception {
    configureScript("""
File f = ['path']
f.mk<caret>
""")
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "mkdir", "mkdirs"
  }

  public void testDeclaredVariableTypeIsMoreImportantThanTheInitializerOne2() throws Exception {
    myFixture.addClass """
public class Some {
    public int prop

    public void f_foo() {}
    public void f_bar() {}
}
"""

    configureScript("""
Some s = [prop: 239]
s.f_<caret>
""")
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "f_foo", "f_bar"
  }

  public void testResolveMethod() throws Exception {
    myFixture.configureByText("a.groovy", """
def foo(File f) {}
@Typed def bar() {
  fo<caret>o(['path'])
}
""")
    def reference = findReference()
    def target = reference.resolve()
    assertEquals "foo", ((GrMethod)target).name
  }

  private PsiReference findReference() {
    return myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
  }

  public void testOverloadingWithConversion() throws Exception {
    myFixture.configureByText("a.groovy", """
def foo(List l) {}
def foo(File f) {}
@Typed def bar() {
  fo<caret>o(['path'])
}
""")
    def reference = findReference()
    def target = reference.resolve()
    assertNotNull target
    assert target.text.contains("List l")
  }

  public void testWrongProperty() throws Exception {
myFixture.configureByText 'a.gpp', '''
class ClassA {
  def prop = 2
  def bar() {
    def t = new Object()
    t.pr<caret>op
  }
}'''
    assert !findReference().resolve()
  }

  public void testCastClosureToOneMethodClass() throws Exception {
    myFixture.addClass """
public abstract class Foo {
  public abstract void foo(String s);
  public abstract void bar(String s);
}
public interface Action {
  void act();
}
"""

    testAssignability """
Foo <warning descr="Cannot assign 'Closure' to 'Foo'">f</warning> = { println it }
Function1<String, Object> f1 = { println it }
Function1<String, Object> f2 = { x=42 -> println x }
Function1<String, Object> <warning descr="Cannot assign 'Closure' to 'Function1<String, Object>'">f3</warning> = { int x -> println x }
Runnable r = { println it }
Action a = { println it }
Action a1 = { a2 = 2 -> println a2 }
"""
  }

  public void testClosureParameterTypesInAssignment() throws Exception {
    configureScript "Function1<String, Object> f = { it.subs<caret> }"
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureParameterTypesInMethodInvocation() throws Exception {
    myFixture.configureByText "a.groovy", """
def foo(int a = 1, Function1<String, Object> f) {}
def foo(String s) {}
def foo(Function2<Integer, String, Object> f) {}

@Typed def bar() {
  foo { it.subsREF }
  foo(1, { it.subsREF })
  foo 1, { it.subsREF }
  foo(1) { it.subsREF }
  foo { a -> a.subsREF }
  foo { a, int b=2 -> a.subsREF }
  foo { a, b -> b.subsREF }
}
"""
    def text = myFixture.file.text
    def pos = 0
    while (true) {
      pos = text.indexOf("REF", pos+1)
      if (pos < 0) {
        break
      }
      myFixture.editor.caretModel.moveToOffset pos
      myFixture.completeBasic()
      try {
        assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
      }
      catch (ComparisonFailure ex) {
        println "at: " + text[0..<pos] + "<caret>" + text[pos..<text.size()]
        throw ex
      }
      LookupManager.getInstance(project).hideActiveLookup()
    }
  }

  public void testReturnTypeOneMethodInterface() throws Exception {
    myFixture.configureByText "a.groovy", """
@Typed Function1<String, Integer> bar() {
   { it.subs<caret> }
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureInMapInstantiation() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo<T> {
  int foo(T a) {}
}

@Typed Foo<String> bar() {
    return [foo: { it.subs<caret> }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testClosureInMapInstantiationBoxPrimitives() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo {
  int foo(int a) {}
}

@Typed Foo bar() {
    return [foo: { it.intV<caret>V }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "intValue"
  }

  public void testClosureInListInstantiation() throws Exception {
    myFixture.configureByText "a.groovy", """
class Foo {
  def Foo(int a, Function1<String, Integer> f) {}
}

@Typed Foo foo() {
  [239, { s -> s.subs<caret> }]
}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.lookupElementStrings, "subSequence", "substring", "substring"
  }

  public void testResolveToStdLib() throws Exception {
    configureScript """
@Typed def foo(List<String> l) {
  l.ea<caret>ch { it.substring(1) }
}
"""
    PsiMethod method = resolveReference().navigationElement as PsiMethod
    assertEquals "each", method.name
    assertEquals "groovypp.util.Iterations", method.containingClass.qualifiedName
  }

  public void testResolveToStdLibWithArrayQualifier() throws Exception {
    configureGppScript """
Integer[] a = []
a.fol<caret>dLeft(2, { a, b -> a+b })
"""
    PsiMethod method = resolveReference().navigationElement as PsiMethod
    assertEquals "foldLeft", method.name
    assertEquals "groovypp.util.Iterations", method.containingClass.qualifiedName
  }

  private PsiElement resolveReference() {
    return findReference().resolve()
  }

  public void testResolveToSuperMethodClosureSyntax() {
    configureScript """
abstract class Super implements Runnable {
  def method(int bar) {}
}

Super s = { <caret>method(2) } as Super
"""
    assert resolveReference() instanceof GrMethod
  }

  public void testMethodTypeParameterInference() throws Exception {
    configureScript """
@Typed package aaa

java.util.concurrent.atomic.AtomicReference<Integer> r = [2]
r.apply { it.intV<caret>i }
"""
    myFixture.completeBasic()
    assertSameElements myFixture.getLookupElementStrings(), "intValue"
  }

  public void testMethodTypeParameterInference2() throws Exception {
    configureScript """
@Typed package aaa

java.util.concurrent.atomic.AtomicReference<Integer> r = [2]
r.apply { it.intV<caret>i } {}
"""
    myFixture.completeBasic()
    assertSameElements myFixture.getLookupElementStrings(), "intValue"
  }

  public void testGotoSuperMethodFromMapLiterals() throws Exception {
    PsiClass point = myFixture.addClass("""
class Point {
  Point() {}
  Point(int y) {}
  int y;
  void setX(int x) {}
  void move(int x, int y) {}
  void move(int y) {}
}""")

    configureScript "Point p = [<caret>y:2]"
    assertEquals point.findFieldByName("y", false), resolveReference()

    configureScript "Point p = [<caret>x:2]"
    assertEquals point.findMethodsByName("setX", false)[0], resolveReference()

    configureScript "Point p = [mo<caret>ve: { x, y -> z }]"
    assertEquals point.findMethodsByName("move", false)[0], resolveReference()

    configureScript "Point p = [mo<caret>ve: ]"
    def resolveResults = multiResolveReference()
    assertSameElements resolveResults.collect { it.element }, point.findMethodsByName("move", false)
  }

  ResolveResult[] multiResolveReference() {
    final PsiReference ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)
    assertNotNull(ref)
    assertInstanceOf(ref, PsiPolyVariantReference)
    return ((PsiPolyVariantReference)ref).multiResolve(true)
  }

  public void testGotoSuperConstructorFromMapLiterals() throws Exception {
    PsiClass point = myFixture.addClass("""
class Point {
  Point() {}
  Point(int y) {}
}""")

    configureGppScript "Point p = [su<caret>per: 2]"
    assertEquals point.constructors[1], resolveReference()

    configureGppScript "Point p = [su<caret>per: [2]]"
    assertEquals point.constructors[1], resolveReference()

    configureGppScript "Point p = ['su<caret>per': []]"
    assertEquals point.constructors[0], resolveReference()

    configureGppScript "Point p = ['su<caret>per': 'a']"
    assertEquals 1, multiResolveReference().size()
  }

  public void testGotoClassFromLiteralOnsetsWhenNoConstructorsPresent() throws Exception {
    PsiClass point = myFixture.addClass(""" class Point { }""")
    configureGppScript "Point p = <caret>[super: 2]"
    assertEquals point, resolveReference()

    configureGppScript "Point p = <caret>[]"
    assertEquals point, resolveReference()
  }

  public void testNoGotoObjectFromLiteral() throws Exception {
    myFixture.addClass(""" class Point { }""")

    configureGppScript "def p = <caret>[]"
    assertNull findReference()
  }

  public void testHighlightInapplicableLiteralConstructor() throws Exception {
    myFixture.addClass("""
class Point {
  Point() {}
}""")

    configureGppScript """
def foo(Point p) {}
Point p = [:]
Point p2 = [super:warning descr="Cannot find constructor of 'Point'">[4, 2]</warning>]
foo(<warning descr="Cannot find constructor of 'Point'">[4, 2]</warning>)
"""
  }

  public void testResolveTraitMethod() throws Exception {
    configureScript """
@Trait
class Some {
	public void doSmth() { println "hello" }
}
Some s
s.do<caret>Smth()
"""
    assertEquals "doSmth", ((PsiMethod) findReference().resolve()).name
  }

  public void testBaseConstructorCallInMapLiteras() throws Exception {
    configureScript """
@Typed File foo() { <warning descr="Constructor 'File' in 'java.io.File' cannot be applied to '(['super':[java.lang.String]])'">['super':['a']]</warning> }
@Typed File goo() { <warning descr="Constructor 'File' in 'java.io.File' cannot be applied to '([:])'">[:]</warning> }
File bar() { <warning descr="Constructor 'File' in 'java.io.File' cannot be applied to '([:])'">[:]</warning> }
"""
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    myFixture.checkHighlighting(true, false, false)
  }

  public void testNestedLiteralConstructors() throws Exception {
    configureGppScript """
    class Foo {
      def Foo(Bar b) { }
    }

    class Bar {
      def Bar(int i) { }
    }

    Foo x = <warning descr="Constructor 'Foo' in 'Foo' cannot be applied to '([java.lang.Integer])'">[[2]]</warning>
    println x
"""
    myFixture.enableInspections new GroovyAssignabilityCheckInspection()
    myFixture.checkHighlighting(true, false, false)
  }

  public void testNoReturnTypeInferenceInTypedContext() throws Exception {
    configureGppScript """
class Foo {
  def foo() { "aaa" }
}
new Foo().foo().substr<caret>a
"""
    assertEmpty myFixture.completeBasic()
  }

  public void testDeclaredReturnTypeInTypedContext() throws Exception {
    configureGppScript """
class Foo {
  String getFoo() { "aaa" }
}
new Foo().foo.substr<caret>a
"""
    myFixture.completeBasic()
    assertOrderedEquals myFixture.lookupElementStrings, "substring", "substring"
  }

  public void testNonInitializedVariable() throws Exception {
    configureScript """

@Typed
def foo() {
  int a
  return a
}

def bar() {
  int a
  return <warning descr="Variable 'a' might not be assigned">a</warning>
}"""
    myFixture.enableInspections new UnassignedVariableAccessInspection()
    myFixture.checkHighlighting(true, false, false)
  }

  public void testExternalizable() throws Exception {
    configureScript '''
@Typed class Foo implements Externalizable {}
<error descr="Method 'writeExternal' is not implemented">class Bar implements Externalizable</error> {}
'''
    myFixture.checkHighlighting(true, false, false)
  }

  public void testUsedInterceptors() {
    configureGppScript '''
class Bar {
  Object getUnresolvedProperty(String <warning descr="Parameter name is unused">name</warning>) {}
  Object <warning descr="Method getUnresolvedProperty is unused">getUnresolvedProperty</warning>(int <warning descr="Parameter name is unused">name</warning>) {}
  void setUnresolvedProperty(String <warning descr="Parameter name is unused">name</warning>, String <warning descr="Parameter value is unused">value</warning>) {}
  int invokeUnresolvedMethod(String <warning descr="Parameter name is unused">name</warning>, String <warning descr="Parameter arg1 is unused">arg1</warning>, boolean <warning descr="Parameter arg2 is unused">arg2</warning>, Object... <warning descr="Parameter args is unused">args</warning>) {}
  int invokeUnresolvedMethod(String <warning descr="Parameter name is unused">name</warning>, Object... <warning descr="Parameter args is unused">args</warning>) {}
  int <warning descr="Method invokeUnresolvedMethod is unused">invokeUnresolvedMethod</warning>(Object... <warning descr="Parameter args is unused">args</warning>) {}
}
println new Bar().zzz
'''
    myFixture.enableInspections(new GroovyUnusedDeclarationInspection(), new UnusedDeclarationInspection())
    myFixture.checkHighlighting(true, false, false)
  }

}

class GppProjectDescriptor extends DefaultLightProjectDescriptor {
  public static final instance = new GppProjectDescriptor()

  @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    final Library.ModifiableModel modifiableModel = model.moduleLibraryTable.createLibrary("GROOVY++").modifiableModel;
    modifiableModel.addRoot(JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.absoluteTestDataPath + "mockGroovypp/groovypp-0.9.0_1.8.2.jar!/"), OrderRootType.CLASSES)
    modifiableModel.addRoot(JarFileSystem.instance.refreshAndFindFileByPath(TestUtils.mockGroovy1_7LibraryName + "!/"), OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
