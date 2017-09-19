/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.highlighting

import com.siyeh.ig.junit.AbstractTestClassNamingConvention
import com.siyeh.ig.junit.TestClassNamingConvention
import com.siyeh.ig.naming.NewClassNamingConventionInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

/**
 * @author peter
 */
class GroovyHighlightingTest extends GrHighlightingTestBase {

  void testDuplicateClosurePrivateVariable() {
    doTest()
  }

  void testClosureRedefiningVariable() {
    doTest()
  }

  void testCircularInheritance() {
    doTest()
  }

  void testEmptyTupleType() {
    doTest()
  }

  void testMapDeclaration() {
    doTest()
  }

  void testShouldNotImplementGroovyObjectMethods() {
    addGroovyObject()
    myFixture.addFileToProject("Foo.groovy", "class Foo {}")
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java")
  }

  void testJavaClassImplementingGroovyInterface() {
    addGroovyObject()
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}")
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java")
  }

  void testDuplicateFields() {
    doTest()
  }

  void testNoDuplicationThroughClosureBorder() {
    myFixture.addClass("package groovy.lang; public interface Closure {}")
    doTest()
  }

  void testRecursiveMethodTypeInference() {
    doTest()
  }

  void testSuperClassNotExists() {
    doRefTest()
  }

  void testAnonymousClassConstructor() { doTest() }

  void testAnonymousClassAbstractMethod() { doTest() }

  //public void _testAnonymousClassStaticMethod() { doTest(); }

  void testAnonymousClassShouldImplementMethods() { doTest() }

  void testAnonymousClassShouldImplementSubstitutedMethod() { doTest() }

  void testUnresolvedLhsAssignment() { doRefTest() }

  void testUnresolvedAccess() { doRefTest() }

  void testBooleanProperties() { doRefTest() }

  void testDuplicateInnerClass() { doTest() }

  void testThisInStaticContext() { doTest() }

  void testLocalVariableInStaticContext() { doTest() }

  void testModifiersInPackageAndImportStatements() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/" + getTestName(false) + ".groovy")
    myFixture.testHighlighting(true, false, false, "x/" + getTestName(false) + ".groovy")
  }

  void testBreakOutside() { doTest() }

  void testUndefinedLabel() { doTest() }

  void testNestedMethods() {
    doTest()
  }

  void testRawOverriddenMethod() { doTest() }

  void testFQNJavaClassesUsages() {
    doTest()
  }

  void testGstringAssignableToString() { doTest() }

  void testGstringAssignableToStringInClosureParameter() { doTest() }

  void testEachOverRange() { doTest() }

  void testEllipsisParam() {
    myFixture.configureByText('a.groovy', '''\
class A {
  def foo(int... x){}
  def foo(int<error descr="Ellipsis type is not allowed here">...</error> x, double y) {}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  void testStringAndGStringUpperBound() { doTest() }

  void testWithMethod() { doTest() }

  void testArrayLikeAccess() { doTest() }

  void testSetInitializing() { doTest() }

  void testEmptyTupleAssignability() { doTest() }

  void testGrDefFieldsArePrivateInJavaCode() {
    myFixture.configureByText("X.groovy", "public class X{def x=5}")
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java")
  }

  void testSuperConstructorInvocation() { doTest() }

  void testDuplicateMapKeys() { doTest() }

  void testIndexPropertyAccess() { doTest() }

  void testPropertyAndFieldDeclaration() { doTest() }

  void testGenericsMethodUsage() { doTest() }

  void testWildcardInExtendsList() { doTest() }

  void testOverrideAnnotation() { doTest() }

  void testClosureCallWithTupleTypeArgument() { doTest() }

  void testMethodDuplicates() { doTest() }

  void testAmbiguousCodeBlock() { doTest() }

  void testAmbiguousCodeBlockInMethodCall() { doTest() }

  void testNotAmbiguousClosableBlock() { doTest() }

  void testDuplicateParameterInClosableBlock() { doTest() }

  void testCyclicInheritance() { doTest() }

  void testNoDefaultConstructor() { doTest() }

  void testNumberDuplicatesInMaps() { doTest() }

  void testBuiltInTypeInstantiation() { doTest() }

  void testSOEInFieldDeclarations() { doTest() }

  void testWrongAnnotation() { doTest() }

  void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java")
    doTest()
  }

  void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy")
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java")
  }

  void testSOFInDelegate() {
    doTest()
  }

  void testMethodImplementedByDelegate() {
    doTest()
  }

  //public void _testTestMarkupStubs() {
  //  doRefTest()
  //}

  void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy",
                              "List<? extends String> la = []; la.get(1); " +
                              "List<? super String> lb = []; lb.get(1); " +
                              "List<?> lc = []; lc.get(1); "
    )
    myFixture.checkHighlighting(true, false, false)
  }

  void testDuplicatedNamedArgs() { doTest() }

  void testConstructorWithAllParametersOptional() {
    doTest()
  }

  void testTupleConstructorAttributes() {
    doTest(new GroovyAssignabilityCheckInspection())
  }

  void testDelegatedMethodIsImplemented() {
    doTest()
  }

  void testEnumImplementsAllGroovyObjectMethods() {
    doTest()
  }

  //public void _testBuilderMembersAreNotUnresolved() {
  //  doRefTest();
  //}

  void testRecursiveConstructors() {
    doTest()
  }

  void testImmutableConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.Immutable class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo<error>()</error>;
  new Foo<error>(2)</error>;
  new Foo(2, "3");
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  void testTupleConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.TupleConstructor class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo();
  new Foo(2);
  new Foo(2, "3");
  new Foo<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  void testInheritConstructorsFromJava() {
    myFixture.addFileToProject "a.groovy", '''
class Person {
  Person(String first, String last) { }
  Person(String first, String last, String address) { }
  Person(String first, String last, int zip) { }
}

@groovy.transform.InheritConstructors
class PersonAge extends Person {
  PersonAge(String first, String last, int zip) { }
}
'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new PersonAge("a", "b");
  new PersonAge("a", "b", "c");
  new PersonAge("a", "b", 239);
  new PersonAge<error>(2, "3", 9)</error>;
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  void testDefaultInitializersAreNotAllowedInAbstractMethods() { doTest() }

  void testConstructorTypeArgs() { doTest() }

  void testIncorrectEscaping() { doTest() }

  void testExtendingOwnInner() { doTest() }

  void testRegexInCommandArg() { doTest() }

  void testJUnitConvention() {
    myFixture.addClass("package junit.framework; public class TestCase {}")
    def inspection = new NewClassNamingConventionInspection()
    inspection.setEnabled(true, TestClassNamingConvention.TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
    inspection.setEnabled(true, AbstractTestClassNamingConvention.ABSTRACT_TEST_CLASS_NAMING_CONVENTION_SHORT_NAME)
    doTest(inspection)
  }

  void testDuplicateMethods() {
    myFixture.configureByText('a.groovy', '''\
class A {
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo()</error>{}
  <error descr="Method with signature foo() is already defined in the class 'A'">def foo(def a=null)</error>{}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  void testPrivateTopLevelClassInJava() {
    myFixture.addFileToProject('pack/Foo.groovy', 'package pack; private class Foo{}')
    myFixture.configureByText('Abc.java', '''\
import pack.Foo;

class Abc {
  void foo() {
    System.out.print(new Foo()); // top-level Groovy class can't be private
  }
}
''')

    myFixture.testHighlighting(false, false, false)
  }

  void testDelegateToMethodWithItsOwnTypeParams() {
    myFixture.configureByText('a.groovy', '''\
interface I<S> {
    def <T> void foo(List<T> a);
}

class Foo {
    @Delegate private I list
}

<error descr="Method 'foo' is not implemented">class Bar implements I</error> {
  def <T> void foo(List<T> a){}
}

class Baz implements I {
  def void foo(List a){}
}
''')

    myFixture.testHighlighting(false, false, false)
  }

  void testMethodDelegate() {
    myFixture.addClass('''\
package groovy.lang;
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Delegate {}
''')
    myFixture.configureByText('a.groovy','''
class A {
  def foo(){}
}

class B {
  @Delegate A getA(){return new A()}
}

new B().foo()
''')

    fixture.checkHighlighting()
  }

  void testMethodDelegateError() {
    myFixture.configureByText('a.groovy','''
class A {
  def foo(){}
}

class B {
  <error>@Delegate</error> A getA(int i){return new A()}
}

new B().foo()
''')

    fixture.checkHighlighting()
  }


  void testBuilderSimpleStrategyError() {
    myFixture.addClass('''\
package groovy.transform.builder;
@Target({ ElementType.TYPE})

public @interface Builder {
  Class<?> builderStrategy();
  boolean includeSuperProperties() default false;
}
''')

    myFixture.addClass('''
package groovy.transform.builder;
public class SimpleStrategy {}
''')

    myFixture.configureByText('a.groovy', '''
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

<error>@Builder(builderStrategy = SimpleStrategy, includeSuperProperties = true)</error>
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}
''')

    fixture.checkHighlighting()
  }

  void testBuilderSimpleStrategy() {
    myFixture.addClass('''\
package groovy.transform.builder;
@Target({ ElementType.TYPE})

public @interface Builder {
  Class<?> builderStrategy();
  boolean includeSuperProperties() default false;
}
''')

    myFixture.addClass('''
package groovy.transform.builder;
public class SimpleStrategy {}
''')

    myFixture.configureByText('a.groovy', '''
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

@Builder(builderStrategy = SimpleStrategy)
class Pojo {
    String name
    def dynamic
    int counter

    def method() {}
}
new Pojo().setName("sd").setCounter(5)
''')

    fixture.checkHighlighting()
  }


  void testPrimitiveTypeParams() {
    myFixture.configureByText('a.groovy', '''\
List<<error descr="Primitive type parameters are not allowed in type parameter list">int</error>> list = new ArrayList<int><EOLError descr="'(' expected"></EOLError>
List<? extends <error descr="Primitive bound types are not allowed">double</error>> l = new ArrayList<double>()
List<?> list2
''')
    myFixture.testHighlighting(true, false, false)
  }

  void testAliasInParameterType() {
    myFixture.configureByText('a_.groovy', '''\
import java.awt.event.ActionListener
import java.awt.event.ActionEvent as AE

public class CorrectImplementor implements ActionListener {
  public void actionPerformed (AE e) { //AE is alias to ActionEvent
  }
}

<error descr="Method 'actionPerformed' is not implemented">public class IncorrectImplementor implements ActionListener</error> {
  public void actionPerformed (Object e) {
  }
}
''')
    myFixture.testHighlighting(true, false, false)
  }

  void testReassignedHighlighting() {
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy")
  }

  void testInstanceOf() {
    myFixture.configureByText('_a.groovy', '''\
class DslPointcut {}

private def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
        return DslPointcut.bind(arg)
    }
    return arg
}''')
    myFixture.testHighlighting(true, false, false)
  }

  void testIncorrectTypeArguments() {
    myFixture.configureByText('_.groovy', '''\
class C <T extends String> {}
C<<warning descr="Type parameter 'java.lang.Double' is not in its bound; should extend 'java.lang.String'">Double</warning>> c
C<String> c2
C<warning descr="Wrong number of type arguments: 2; required: 1"><String, Double></warning> c3
''')
    myFixture.testHighlighting(true, false, true)
  }

  void testTryCatch1() {
    testHighlighting('''\
try {}
catch (Exception e){}
catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
''')
  }

  void testTryCatch2() {
    testHighlighting('''\
try {}
catch (e){}
catch (<warning descr="Exception 'java.lang.Exception' has already been caught">e</warning>){}
''')
  }

  void testTryCatch3() {
    testHighlighting('''\
try {}
catch (e){}
catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
''')
  }

  void testTryCatch4() {
    testHighlighting('''\
try {}
catch (Exception | <warning descr="Unnecessary exception 'java.io.IOException'. 'java.lang.Exception' is already declared">IOException</warning> e){}
''')
  }

  void testTryCatch5() {
    testHighlighting('''\
try {}
catch (RuntimeException | IOException e){}
catch (<warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
''')
  }

  void testTryCatch6() {
    testHighlighting('''\
try {}
catch (NullPointerException | IOException e){}
catch (ClassNotFoundException | <warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
''')
  }

  void testCompileStatic() {
    myFixture.addClass('''\
package groovy.transform;
public @interface CompileStatic {
}''')

    testHighlighting('''\
import groovy.transform.CompileStatic

class A {

def foo() {
print <warning descr="Cannot resolve symbol 'abc'">abc</warning>
}

@CompileStatic
def bar() {
print <error descr="Cannot resolve symbol 'abc'">abc</error>
}
}
''', true, false, false, GrUnresolvedAccessInspection)
  }

  void testUnresolvedVarInStaticMethod() {
    testHighlighting('''\
static def foo() {
  print <error descr="Cannot resolve symbol 'abc'">abc</error>

  def cl = {
     print <warning descr="Cannot resolve symbol 'cde'">cde</warning>
  }
}
''', GrUnresolvedAccessInspection)
  }

  void testStaticOkForClassMembersWithThisQualifier() {
    testHighlighting('''\
class A {
  def foo(){}
  static bar() {
    this.toString()
    this.getFields()
    this.<warning descr="Cannot reference non-static symbol 'foo' from static context">foo</warning>()
  }
}
''', GrUnresolvedAccessInspection)
  }

  void testScriptFieldsAreAllowedOnlyInScriptBody() {
    addGroovyTransformField()
    testHighlighting('''\
import groovy.transform.Field

@Field
def foo

def foo() {
  <error descr="Annotation @Field can only be used within a script body">@Field</error>
  def bar
}

class X {
  <error descr="Annotation @Field can only be used within a script">@Field</error>
  def bar

  def b() {
    <error descr="Annotation @Field can only be used within a script">@Field</error>
    def x
  }
}
''')
  }

  void testDuplicatedScriptField() {
    addGroovyTransformField()
    testHighlighting('''\
import groovy.transform.Field

while(true) {
  @Field def <error descr="Field 'foo' already defined">foo</error>
}

while(false) {
  @Field def <error descr="Field 'foo' already defined">foo</error>
}

while(i) {
  def foo
}

def foo
''')
  }

  void testReturnTypeInStaticallyCompiledMethod() {
    addCompileStatic()
    testHighlighting('''\
import groovy.transform.CompileStatic
@CompileStatic
int method(x, y, z) {
    if (x) {
        <error descr="Cannot return 'String' from method returning 'int'">'String'</error>
    } else if (y) {
        42
    } else if (z) {
      <error descr="Cannot return 'String' from method returning 'int'">return</error> 'abc'
    } else {
      return 43
    }
}
''')
  }

  void testReassignedVarInClosure1() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

@CompileStatic
def test() {
    def var = "abc"
    def cl = {
        var = new Date()
    }
    cl()
    var.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>()
}
""", GrUnresolvedAccessInspection)
  }

  void testReassignedVarInClosure2() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

@CompileStatic
def test() {
    def cl = {
        def var
        var = new Date()
    }
    def var = "abc"

    cl()
    var.toUpperCase()  //no errors
}
""", GrUnresolvedAccessInspection)
  }

  void testReassignedVarInClosure3() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

@CompileStatic
def test() {
    def var = "abc"
    def cl = new Closure(this, this){
      def call() {
        var = new Date()
      }
    }
    cl()
    var.toUpperCase() //no errors
}
""", GrUnresolvedAccessInspection)
  }

  void testReassignedVarInClosure4() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

class X {
  def var
}

@CompileStatic
def test() {
    def var = "abc"
    new X().with {
        var = new Date()
    }

    var.<error descr="Cannot resolve symbol 'toUpperCase'">toUpperCase</error>()
}
""", GrUnresolvedAccessInspection)
  }

  void testOverrideForVars() {
    testHighlighting('''\
class S {
  @<error descr="'@Override' not applicable to field">Override</error> def foo;

  def bar() {
   @<error descr="'@Override' not applicable to local variable">Override</error> def x
  }
}''')
  }

  void testUnusedImportToList() {
    myFixture.addClass('''package java.awt; public class Component{}''')
    testHighlighting('''\
import java.awt.Component
<warning descr="Unused import">import java.util.List</warning>

print Component
print List
''')
  }

  void testUsedImportToList() {
    myFixture.addClass('''package java.awt; public class Component{}''')
    myFixture.addClass('''package java.awt; public class List{}''')
    myFixture.addClass('''package java.util.concurrent; public class ConcurrentHashMap{}''')
    testHighlighting('''\
import java.awt.*
import java.util.List
<warning descr="Unused import">import java.util.concurrent.ConcurrentHashMap</warning>

print Component
print List
''')
  }

  void testIncompatibleTypeOfImplicitGetter() {
    testHighlighting('''\
abstract class Base {
    abstract String getFoo()
}

class Inheritor extends Base {
    final <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">foo</error> = '3'
}''')
  }

  void testIncompatibleTypeOfInheritedMethod() {
    testHighlighting('''\
abstract class Base {
  abstract String getFoo()
}

class Inheritor extends Base {
    def <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">getFoo</error>() {''}
}''')
  }

  void testIncompatibleTypeOfInheritedMethod2() {
    testHighlighting('''\
abstract class Base {
  abstract String getFoo()
}

class Inheritor extends Base {
    <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">Object</error> getFoo() {''}
}''')
  }

  void testIncompatibleTypeOfInheritedMethodInAnonymous() {
    testHighlighting('''\
abstract class Base {
  abstract String getFoo()
}

new Base() {
    <error descr="The return type of java.lang.Object getFoo() in anonymous class derived from Base is incompatible with java.lang.String getFoo() in Base">Object</error> getFoo() {''}
}''')
  }

  void testAnnotationArgs() {
    testHighlighting('''\
@interface Int {
  int value()
  String s() default 'a'
}

@Int(<error descr="Cannot assign 'String' to 'int'">'a'</error>) def foo(){}

@Int(2) def bar(){}

@Int(value = 2) def c(){}

@Int(value = 3, s = <error descr="Cannot assign 'Integer' to 'String'">4</error>) def x(){}

@Int(value = 3, s = '4') def y(){}
''')
  }

  void testDefaultAttributeValue() {
    testHighlighting('''\
@interface Int {
  int value1() default 2
  String value2() default <error descr="Cannot assign 'Integer' to 'String'">2</error>
  String value3()
}
''')
  }

  void testAnnotationAttributeTypes() {
    testHighlighting('''\
@interface Int {
  int a()
  String b()
  <error descr="Unexpected attribute type: 'PsiType:Date'">Date</error> c()
  Int d()
  int[] e()
  String[] f()
  boolean[][] g()
  <error descr="Unexpected attribute type: 'PsiType:Boolean'">Boolean</error>[] h()
  Int[][][][] i()
}
''')
  }

  void testDefaultAnnotationValue() {
    testHighlighting('''\
@interface A {
  int a() default 2
  String b() default <error descr="Cannot assign 'List<String>' to 'String'">['a']</error>
  String[][] c() default <error descr="Cannot assign 'String' to 'String[][]'">'f'</error>
  String[][] d() default [['f']]
  String[][] e() default [[<error descr="Cannot assign 'List<String>' to 'String'">['f']</error>]]
}
''')
  }

  void testAbstractMethodWithBody() {
    testHighlighting('''\
interface A {
  def foo()<error descr="Abstract methods must not have body">{}</error>
}

abstract class B {
  abstract foo()<error descr="Abstract methods must not have body">{}</error>
}

class X {
  def foo(){}
}
''')
  }

  void testTupleVariableDeclarationWithRecursion() {
    testHighlighting('''def (a, b) = [a, a]''')
  }

  void testSwitchInLoopNoSoe() {
    testHighlighting('''
def foo(File f) {
  while (true) {
    switch (f.name) {
      case 'foo': f = new File('bar')
    }
    if (f) {
      return
    }
  }
}''')

  }

  void testInstanceMethodUsedInStaticClosure() {
    testHighlighting('''\
class A {
    static staticClosure = {
        foo()
    }

    def staticMethod() {
        foo()
    }

    def foo() { }
}
''')
  }

  void testStaticOk() {
    testHighlighting('''\
class A {
  class B {}
}

A.B foo = new A.<warning descr="Cannot reference non-static symbol 'A.B' from static context">B</warning>()
''', GrUnresolvedAccessInspection)
  }

  void testDuplicatedVar0() {
    testHighlighting('''\
def a = 5
def <error descr="Variable 'a' already defined">a</error> = 7
''')
  }

  void testDuplicatedVarInIf() {
    testHighlighting('''\
def a = 5
if (cond)
  def <error descr="Variable 'a' already defined">a</error> = 7
''')
  }


  void testDuplicatedVarInAnonymous() {
    testHighlighting('''\
def foo() {
  def a = 5

  new Runnable() {
    void run() {
      def <error descr="Variable 'a' already defined">a</error> = 7
    }
  }
}
''')
  }

  void testDuplicatedVarInClosure() {
    testHighlighting('''\
def foo() {
  def a = 5

  [1, 2, 3].each {
    def <error descr="Variable 'a' already defined">a</error> = 7
  }
}
''')
  }

  void testDuplicatedVarInClosureParameter() {
    testHighlighting('''\
def foo() {
  def a = 5

  [1, 2, 3].each {<error descr="Variable 'a' already defined">a</error> ->
    print a
  }
}
''')
  }

  void testDuplicatedVarInAnonymousParameter() {
    testHighlighting('''\
def a = -1

def foo() {
  def a = 5

  new Object() {
    void bar(int <error descr="Variable 'a' already defined">a</error>) {}
  }
}
''')
  }


  void testNoDuplicateErrors() {
    testHighlighting('''\
class Foo {
  def foo

  def abr(def foo) {}
}

class Test {
  def foo

  def bar() {
    def foo
  }
}

class X {
  def foo

  def x = new Runnable() {
    void run() {
      def foo
    }
  }
}
''')
  }

  void testPropertySelectionMayBeLValue() {
    testHighlighting('''\
def methodMissing(String methodName, args) {
    def closure = {
        callSomeOtherMethodInstead()
    }
    this.metaClass."$methodName" = closure
    <error descr="Invalid value to assign to">closure()</error> = 2
}
''')
  }

  void testEnumExtendsList() {
    testHighlighting('''\
enum Ee <error descr="Enums may not have 'extends' clause">extends Enum</error> {
}
''')
  }

  void testVarInTupleDuplicate() {
    testHighlighting('''\
def (a, b) = []
def (<error descr="Variable 'b' already defined">b</error>, c, <error descr="Variable 'c' already defined">c</error>) = []
''')
  }

  void 'test create method from usage is available in static method'() {
    myFixture.enableInspections(GrUnresolvedAccessInspection)
    testHighlighting('''\
class A {
  static foo() {
    <warning descr="Cannot resolve symbol 'abc'">a<caret>bc</warning>()
  }
}
''')

    assertNotNull(myFixture.findSingleIntention("Create method 'abc'"))
  }

  void testTypeParameterIsCorrect() {
    testHighlighting('''\
class Component{}

class Window extends Component{
    Window(i) {
    }
}

class Super <C extends Component> {

}

class Sub<W extends Window> extends Super<W> {
}
''')
  }

  void 'test anonymous body on new line within parenthesized expression'() {
    testHighlighting '''\
class Foo {
  def i
}

def foo = new Foo()
<error descr="Ambiguous code block">{
}</error>

def bar = (new Foo()
{
})

def baz
baz = new Foo()
<error descr="Ambiguous code block">{
}</error>

baz = (new Foo()
{
})

(baz = new Foo()
{
})

new Foo()
<error descr="Ambiguous code block">{
}</error>

(new Foo()
{
})

new Foo()
<error descr="Ambiguous code block">{
}</error> + 666

(new Foo()
{
}) + 444

1 + (new Foo()
{
} + 555)

(1 + new Foo()
{
} + 112)

new Foo()
<error descr="Ambiguous code block">{
}</error>.getI()

(new Foo()
{
}).getI()

(new Foo()
{
}.getI())

def mm() {
    new Foo()
    <error descr="Ambiguous code block">{
    }</error>
}

def mm2() {
    (new Foo()
    {
    })
}

def mm3() {
    return new Foo()
    <error descr="Ambiguous code block">{
    }</error>
}

def mm4() {
    return (new Foo()
    {
    })
}

(new Foo()
{
    def foo() {
        // still error
        new Foo()
        <error descr="Ambiguous code block">{
        }</error>
    }
})
'''
  }

  void 'test anonymous body on new line within argument list'() {
    testHighlighting '''\
class Foo {}

def foo(param) {}

foo(new Foo()
{
})

def baz
foo(baz = new Foo()
{
})

foo(new Foo()
{
} + 666)

foo(1 + new Foo()
{
})

foo(1 + new Foo()
{
} + 666)

foo(new Foo()
{
}.getI())

foo(new Foo()
{

}.identity { it })

foo new Foo()
<error descr="Ambiguous code block">{

}</error>

foo 1 + (new Foo()
{
}) + 22

foo(new Foo() {
    def a() {
        // still error
        new Foo()
        <error descr="Ambiguous code block">{

        }</error>
    }
})
'''
  }

  void testGenerics() {
    addHashSet()
    testHighlighting('''

class NodeInfo{}

interface NodeEvent<T> {}

interface TrackerEventsListener<N extends NodeInfo, E extends NodeEvent<N>> {
    void onEvents(Collection<E> events)
}

class AgentInfo extends NodeInfo {}

print new HashSet<TrackerEventsListener<AgentInfo, NodeEvent<AgentInfo>>>() //correct
print new HashSet<TrackerEventsListener<AgentInfo, <warning descr="Type parameter 'NodeEvent<java.lang.Object>' is not in its bound; should extend 'NodeEvent<N>'">NodeEvent<Object></warning>>>() //incorrect
''')
  }

  void testTypeInConstructor() {
    testHighlighting('''\
class X {
  public <error descr="Return type element is not allowed in constructor">void</error> X() {}
}
''')
  }

  void testFinalMethodOverriding() {
    testHighlighting('''\
class A {
    final void foo() {}
}

class B extends A{
    <error descr="Method 'foo()' cannot override method 'foo()' in 'A'; overridden method is final">void foo()</error> {}
}
''')
  }

  void testWeakerMethodAccess0() {
    testHighlighting('''\
class A {
    void foo() {}
}

class B extends A{
    <error descr="Method 'foo()' cannot have weaker access privileges ('protected') than 'foo()' in 'A' ('public')">protected</error> void foo() {}
}
''')
  }

  void testWeakerMethodAccess1() {
    testHighlighting('''\
class A {
    void foo() {}
}

class B extends A{
    <error descr="Method 'foo()' cannot have weaker access privileges ('private') than 'foo()' in 'A' ('public')">private</error> void foo() {}
}
''')
  }

  void testWeakerMethodAccess2() {
    testHighlighting('''\
class A {
    public void foo() {}
}

class B extends A{
    void foo() {} //don't highlight anything
}
''')
  }

  void testWeakerMethodAccess3() {
    testHighlighting('''\
class A {
    protected void foo() {}
}

class B extends A{
    <error descr="Method 'foo()' cannot have weaker access privileges ('private') than 'foo()' in 'A' ('protected')">private</error> void foo() {}
}
''')
  }

  void testOverriddenProperty() {
    testHighlighting('''\
class A {
    final foo = 2
}

class B extends A {
    <error descr="Method 'getFoo()' cannot override method 'getFoo()' in 'A'; overridden method is final">def getFoo()</error>{5}
}
''')
  }

  void testUnresolvedQualifierHighlighting() {
    testHighlighting('''\
<error descr="Cannot resolve symbol 'Abc'">Abc</error>.Cde abc
''')
  }

  void testVarargParameterWithoutTypeElement() {
    testHighlighting('''\
def foo(def <error descr="Ellipsis type is not allowed here">...</error> vararg, def last) {}
''')
  }

  void testTupleInstanceCreatingInDefaultConstructor() {
    testHighlighting('''
class Book {
    String title
    Author author

    String toString() { "$title by $author.name" }
}

class Author {
    String name
}

def book = new Book(title: "Other Title", author: [name: "Other Name"])

assert book.toString() == 'Other Title by Other Name'
''')
  }

  void testArrayAccessForMapProperty() {

    testHighlighting('''\
def bar() {
    return [list:[1, 2, 3]]
}

def testConfig = bar()
print testConfig.list[0]
print testConfig.<warning descr="Cannot resolve symbol 'foo'">foo</warning>()
''', true, false, false, GrUnresolvedAccessInspection)
  }

  void testGStringInjectionLFs() {
    testHighlighting('''\
print "<error descr="GString injection must not contain line feeds">${
}</error>"

print """${
}"""

print "<error descr="GString injection must not contain line feeds">${ """
"""}</error>"
''')
  }

  void testListOrMapErrors() {
    testHighlighting('''\
print([1])
print([1:2])
print(<error descr="Collection literal contains named and expression arguments at the same time">[1:2, 4]</error>)
''')
  }

  void _testDelegatesToApplicability() {
    testHighlighting('''
      def with(@DelegatesTo.Target Object target, @DelegatesTo Closure arg) {
        arg.delegate = target
        arg()
      }

      def with2(Object target, @<error descr="Missed attributes: value">DelegatesTo</error> Closure arg) {
        arg.delegate = target
        arg()
      }
''')
  }

  void testClosureParameterInferenceDoesNotWorkIfComplieStatic() {
    addCompileStatic()
    myFixture.enableInspections(GrUnresolvedAccessInspection)
    testHighlighting('''
@groovy.transform.CompileStatic
def foo() {
    final collector = [1, 2].find {a ->
        a.<error descr="Cannot resolve symbol 'intValue'">intValue</error>()
    }
}
''')
  }

  void testIllegalLiteralName() {
    testHighlighting('''
def <error descr="Illegal escape character in string literal">'a\\obc'</error>() {

}
''')
  }

  void testExceptionParameterAlreadyDeclared() {
    testHighlighting('''
      int missing() {
        InputStream i = null

        try {
          return 1
        }
        catch(Exception <error descr="Variable 'i' already defined">i</error>) {
          return 2
        }
      }
    ''')
  }

  void testInnerAnnotationType() {
    testHighlighting('''
      class A {
        @interface <error descr="Annotation type cannot be inner">X</error> {}
      }
    ''')
  }

  void testDuplicatingAnnotations() {
    testHighlighting('''\
@interface A {
  String value()
}

@A('a')
@A('a')
class X{}

@A('a')
@A('ab')
class Y{}

<error descr="Duplicate modifier 'public'">public public</error> class Z {}
''')
  }

  void testAnnotationAttribute() {
    testHighlighting('''\
@interface A {
  String value() default 'a'
  String[] values() default []
}


@A('abc')
def x

@A(<error descr="Expected ''abc' + 'cde'' to be an inline constant">'abc' + 'cde'</error>)
def y

class C {
  final static String CONST1 = 'ABC'
  final static String CONST2 = 'ABC' + 'CDE'
  final        String CONST3 = 'ABC'
}

@A(C.CONST1)
def z

@A(<error descr="Expected ''ABC' + 'CDE'' to be an inline constant">C.CONST2</error>)
def a

@A(C.CONST3)
def b

@A(values=['a'])
def c

@A(values=<error descr="Expected ''a'+'b'' to be an inline constant">['a'+'b']</error>)
def d

@A(values=[C.CONST1])
def e

@A(values=<error descr="Expected ''ABC' + 'CDE'' to be an inline constant">[C.CONST1, C.CONST2]</error>)
def f

@interface X {
  Class value()
}

@X(String.class)
def g
''')
  }

  void testDuplicateMethodsWithGenerics() {
    testHighlighting('''\
class A<T, E> {
  <error descr="Method with signature foo(Object) is already defined in the class 'A'">def foo(T t)</error> {}
  <error descr="Method with signature foo(Object) is already defined in the class 'A'">def foo(E e)</error> {}
}

class B {
  <error descr="Method with signature foo(Object) is already defined in the class 'B'">def <T> void foo(T t)</error> {}
  <error descr="Method with signature foo(Object) is already defined in the class 'B'">def <E> void foo(E e)</error> {}
}

class C<T, E> {
  <error descr="Method with signature foo(Object) is already defined in the class 'C'">def foo(T t, T t2 = null)</error> {}
  <error descr="Method with signature foo(Object) is already defined in the class 'C'">def foo(E e)</error> {}
}

class D<T, E> {
  <error descr="Method with signature foo(Object, Object) is already defined in the class 'D'">def foo(T t, E e)</error> {}
  <error descr="Method with signature foo(Object, Object) is already defined in the class 'D'">def foo(E t, T e)</error> {}
  def foo(E t) {}
}''')
  }

  void testOverriddenReturnType0() {
    myFixture.addClass('class Base{}')
    myFixture.addClass('class Inh extends Base{}')
    testHighlighting('''\
class A {
  List<Base> foo() {}
}

class B extends A {
  List<Inh> foo() {} //correct
}
''')
  }

  void testOverriddenReturnType1() {
    myFixture.addClass('class Base extends SuperBase {}')
    myFixture.addClass('class Inh extends Base{}')
    testHighlighting('''\
class A {
  List<Base> foo() {}
}

class B extends A {
  <error>Collection<Base></error> foo() {}
}
''')
  }

  void testOverriddenReturnType2() {
    myFixture.addClass('class Base extends SuperBase {}')
    myFixture.addClass('class Inh extends Base{}')
    testHighlighting('''\
class A {
  List<Base> foo() {}
}

class B extends A {
  <error>int</error> foo() {}
}
''')
  }

  void testOverriddenReturnType3() {
    myFixture.addClass('class Base extends SuperBase {}')
    myFixture.addClass('class Inh extends Base{}')
    testHighlighting('''\
class A {
  Base[] foo() {}
}

class B extends A {
  <error>Inh[]</error> foo() {}
}
''')
  }

  void testOverriddenReturnType4() {
    myFixture.addClass('class Base extends SuperBase {}')
    myFixture.addClass('class Inh extends Base{}')
    testHighlighting('''\
class A {
  Base[] foo() {}
}

class B extends A {
  Base[] foo() {}
}
''')
  }

  void testEnumConstantAsAnnotationAttribute() {
    testHighlighting('''\
enum A {CONST}

@interface I {
    A foo()
}

@I(foo = A.CONST) //no error
def bar
''')
  }

  void testUnassignedFieldAsAnnotationAttribute() {
    testHighlighting('''\
interface A {
  String CONST
}

@interface I {
    String foo()
}

@I(foo = <error descr="Expected 'A.CONST' to be an inline constant">A.CONST</error>)
def bar
''')
  }

  void testFinalFieldRewrite() {
    testHighlighting('''\
class A {
  final foo = 1

  def A() {
    foo = 2 //no error
  }

  def foo() {
    <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
  }
}

new A().foo = 2 //no error
''')
  }

  void testStaticFinalFieldRewrite() {
    testHighlighting('''\
class A {
  static final foo = 1

  def A() {
    <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
  }

  static {
    foo = 2 //no error
  }

  def foo() {
    <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
  }

  static def bar() {
    <error descr="Cannot assign a value to final field 'foo'">foo</error> = 2
  }
}

A.foo = 3 //no error
''')
  }

  void testSOEIfExtendsItself() {
    testHighlighting('''\
<error descr="Cyclic inheritance involving 'A'">class A extends A</error> {
  def foo
}

<error descr="Cyclic inheritance involving 'B'">class B extends C</error> {
  def foo
}

<error descr="Cyclic inheritance involving 'C'">class C extends B</error> {
}
''')
  }

  void testFinalParameter() {
    testHighlighting('''\
def foo0(final i) {
  <error descr="Cannot assign a value to final parameter 'i'">i</error> = 5
  print i
}

def foo1(i) {
  i = 5
  print i
}

def foo2(final i = 4) {
  <error descr="Cannot assign a value to final parameter 'i'">i</error> = 5
  print i
}

def foo3(final i) {
  print i
}
''')
  }

  void testNonStaticInnerClass1() {
    testHighlighting('''\
class MyController {
     static def list() {
         def myInnerClass = new MyCommand.<error descr="Cannot reference non-static symbol 'MyCommand.MyInnerClass' from static context">MyInnerClass</error>()
         print myInnerClass
    }
}

class MyCommand {
    class MyInnerClass {
    }
}
''', GrUnresolvedAccessInspection)
  }

  void testNonStaticInnerClass2() {
    testHighlighting('''\
class MyController {
     def list() {
         def myInnerClass = new MyCommand.<warning descr="Cannot reference non-static symbol 'MyCommand.MyInnerClass' from static context">MyInnerClass</warning>()
         print myInnerClass
    }
}

class MyCommand {
    class MyInnerClass {
    }
}
''', GrUnresolvedAccessInspection)
  }

  void testNonStaticInnerClass3() {
    myFixture.configureByText('_.groovy', '''\
class MyController {
     static def list() {
         def myInnerClass = new MyCommand.<error descr="Cannot reference non-static symbol 'MyCommand.MyInnerClass' from static context">MyInnerClass</error>()
         print myInnerClass
    }
}

class MyCommand {
    class MyInnerClass {
    }
}
''')

    myFixture.enableInspections(GrUnresolvedAccessInspection)

    GrUnresolvedAccessInspection.getInstance(myFixture.file, myFixture.project).myHighlightInnerClasses = false
    myFixture.testHighlighting(true, false, true)
  }

  void testNonStaticInnerClass4() {
    myFixture.configureByText('_.groovy', '''\
class MyController {
     def list() {
         def myInnerClass = new MyCommand.MyInnerClass()
         print myInnerClass
    }
}

class MyCommand {
    class MyInnerClass {
    }
}
''')

    myFixture.enableInspections(GrUnresolvedAccessInspection)

    GrUnresolvedAccessInspection.getInstance(myFixture.file, myFixture.project).myHighlightInnerClasses = false
    myFixture.testHighlighting(true, false, true)
  }

  void testInnerClassWithStaticMethod() {
    testHighlighting('''\
class A {
    class B {
        static foo() {}

        static bar() {
            B.foo() //correct
        }
    }

    static foo() {
      new <error descr="Cannot reference non-static symbol 'A.B' from static context">B</error>()
    }
}
''')
  }

  void testUnresolvedPropertyWhenGetPropertyDeclared() {
    myFixture.enableInspections(GrUnresolvedAccessInspection)
    myFixture.configureByText('_.groovy', '''\
class DelegatesToTest {
    void ideSupport() {
        define {
            a //delegatesTo provides getProperty from DslDelegate
        }
    }

    private static void define(@DelegatesTo(DslDelegate) Closure dsl) {
    }
}

class DslDelegate {
    def getProperty(String name) {
        {->print 1}
    }


    def ab() {
        print a  //getPropertyDeclared
        <warning descr="Cannot resolve symbol 'a'">a</warning>()      //unresolved
    }
}

print new DslDelegate().foo   //resolved
print new DslDelegate().<warning descr="Cannot resolve symbol 'foo'">foo</warning>() //unresolved
''')

    GrUnresolvedAccessInspection.getInstance(myFixture.file, myFixture.project).myHighlightIfGroovyObjectOverridden = false
    myFixture.testHighlighting(true, false, true)
  }

  void testImplementInaccessibleAbstractMethod() {
    myFixture.addClass('''\
package p;

public abstract class Base {
  abstract void foo();
}
''')
    testHighlighting('''\
<error>class Foo extends p.Base</error> {
}
''')
  }

  void testInjectedLiterals() {
    testHighlighting("""\
//language=Groovy
def groovy1 = '''print 'abc\\' '''

//language=Groovy
def groovy2 = '''print <error descr="String end expected">'abc\\\\' </error>'''

""")
  }

  void testAnnotationAsAnnotationValue() {
    testHighlighting('''\
@interface A {}
@interface B {
  A[] foo()
}
@interface C {
  A foo()
}

@B(foo = @A)
@B(foo = [@A])
@C(foo = @A)
@C(foo = <error descr="Cannot assign 'Integer' to 'A'">2</error>)
def foo
''')
  }

  void testSameNameMethodWithDifferentAccessModifiers() {
    testHighlighting('''

class A {
  def foo(){}
  def foo(int x) {}
}

class B {
  <error descr="Mixing private and public/protected methods of the same name">private def foo()</error>{}
  <error descr="Mixing private and public/protected methods of the same name">public def foo(int x)</error> {}
}

class C {
  private foo(){}
  private foo(int x) {}
}

class D {
  <error>private foo()</error>{}
  <error>protected foo(int x)</error> {}
}

class E {
  <error>private foo()</error>{}
  <error>def foo(int x)</error> {}
}

class Z {
 private Z() {}   //correct
 private Z(x) {}  //correct
}
''')
  }

  void testImmutable() {
    testHighlighting('''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable
  private String mutable

  def foo() {
    <error descr="Cannot assign a value to final field 'immutable'">immutable</error> = 5
    mutable = 5

  }
}
''')
  }

  void testConstructorInImmutable() {
    testHighlighting('''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable
  private String mutable

  def <error descr="Explicit constructors are not allowed for @Immutable class">A</error>() {}
}
''')
  }

  void testGetterInImmutable() {
    testHighlighting('''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable
  private String mutable

  String <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {immutable}
  String getMutable() {mutable}
}
''')
  }

  void testGetterInImmutable2() {
    testHighlighting('''\
import groovy.transform.Immutable

@Immutable
class A {
  String immutable

  int <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {1}
}
''')
  }

  void testMinusInAnnotationArg() {
    testHighlighting('''\
@interface Xx {
    int value()
}

@Xx(-1)
public class Bar1 { }

@Xx(+1)
public class Bar2 { }

@Xx(<error descr="Expected '++1' to be an inline constant">++1</error>)
public class Bar3 { }
''')
  }

  void testImportStaticFix() {
    myFixture.configureByText('a.groovy', '''
class A {
  static void foo(String s){}
}

foo(<caret>)
''')

    myFixture.getAvailableIntention("Static import method 'A.foo'")
  }

  void testInaccessibleWithCompileStatic() {
    addCompileStatic()
    testHighlighting('''
import groovy.transform.CompileStatic

@CompileStatic
class PrivateTest {
    void doTest() {
        Target.<error descr="Access to 'callMe' exceeds its access rights">callMe</error>()
    }
}

class Target {
    private static void callMe() {}
}
''')
  }

  void 'test no exception for @Field annotation without variable'() {
    testHighlighting '''\
import groovy.transform.Field

@Field
def (,<error descr="Identifier expected">)</error> = []
'''
  }

  void 'test no SOE in index property assignment with generic function'() {
    testHighlighting '''
class Main {

    static <T> T foo() {}

    static void main(String[] args) {
        def main = new Main()
        main[Main] = foo()
    }

    def putAt(x, String t) {
        println "Works: $x = $t"
    }
}
'''
    testHighlighting '''
class Main {

    static <T> T foo() {}

    static void main(String[] args) {
        def main = new Main()
        (main[Main]) = foo()
    }

    def putAt(x, String t) {
        println "Works: $x = $t"
    }
}
'''
  }

  void 'test static GDK method applicable'() {
    testHighlighting '''
def doParse() {
  Date.parse('dd.MM.yyyy', '01.12.2016')
}
''', GroovyAssignabilityCheckInspection
  }

  void 'test unresolved anonymous base class'() {
    testHighlighting '''
def foo = new <error descr="Cannot resolve symbol 'Rrrrrrrr'">Rrrrrrrr</error>() {}
'''
  }

  void testLocalVariableModifiers() { doTest() }

  void testFieldModifiers() { doTest() }

  void testScriptFieldModifiers() { doTest() }

  void "test allow and do not highlight 'trait', 'as', 'def', 'in' within package"() {
    myFixture.addClass '''\
package a.b.c.trait.d.as.e.def.f.in.g;
public class Foo {} 
'''
    testHighlighting '''\
import a.b.c.<info>trait</info>.d.<info>as</info>.e.<info>def</info>.f.<info>in</info>.g.*
''', false, true
  }

  void 'test resolve methods of boxed types on primitive qualifiers'() {
    testHighlighting '''\
class Widget {
    float width = 1.1f
}

Widget w = new Widget()
w.width.round()
w.width.intValue()
w.width.compareTo(2f)
''', GrUnresolvedAccessInspection
  }
}
