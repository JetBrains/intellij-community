/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.testFramework.IdeaTestUtil
import com.siyeh.ig.junit.JUnitAbstractTestClassNamingConventionInspection
import com.siyeh.ig.junit.JUnitTestClassNamingConventionInspection
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.confusing.GrUnusedIncDecInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection

/**
 * @author peter
 */
public class GroovyHighlightingTest extends GrHighlightingTestBase {

  public void testDuplicateClosurePrivateVariable() {
    doTest();
  }

  public void testClosureRedefiningVariable() {
    doTest();
  }

  public void testCircularInheritance() {
    doTest();
  }

  public void testEmptyTupleType() {
    doTest();
  }

  public void testMapDeclaration() {
    doTest();
  }

  public void testShouldntImplementGroovyObjectMethods() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testDuplicateFields() {
    doTest();
  }

  public void testNoDuplicationThroughClosureBorder() {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() {
    doTest();
  }

  public void testSuperClassNotExists() {
    doRefTest()
  }

  public void testAnonymousClassConstructor() {doTest();}
  public void testAnonymousClassAbstractMethod() {doTest();}
  public void testAnonymousClassStaticMethod() {doTest();}
  public void testAnonymousClassShoudImplementMethods() {doTest();}
  public void testAnonymousClassShouldImplementSubstitutedMethod() {doTest();}

  public void testUnresolvedLhsAssignment() { doRefTest() }

  public void testUnresolvedAccess() { doRefTest() }
  public void testBooleanProperties() { doRefTest() }

  public void testDuplicateInnerClass() {doTest();}

  public void testThisInStaticContext() {doTest();}
  public void testLocalVariableInStaticContext() {doTest();}

  public void testModifiersInPackageAndImportStatements() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/"+getTestName(false)+".groovy");
    myFixture.testHighlighting(true, false, false, "x/"+getTestName(false)+".groovy");
  }

  public void testBreakOutside() {doTest();}
  public void testUndefinedLabel() {doTest();}

  public void testNestedMethods() {
    doTest();
  }

  public void testRawOverridedMethod() {doTest();}

  public void testFQNJavaClassesUsages() {
    doTest();
  }

  public void testGstringAssignableToString() {doTest();}
  public void testGstringAssignableToStringInClosureParameter() {doTest();}

  public void testEachOverRange() {doTest();}

  public void testEllipsisParam() {
    myFixture.configureByText('a.groovy', '''\
class A {
  def foo(int... x){}
  def foo(<error descr="Ellipsis type is not allowed here">int...</error> x, double y) {}
}
''')
    myFixture.checkHighlighting(true, false, false)
  }

  public void testStringAndGStringUpperBound() {doTest();}

  public void testWithMethod() {doTest();}

  public void testArrayLikeAccess() {doTest();}

  public void testSetInitializing() {doTest();}

  public void testEmptyTupleAssignability() {doTest();}

  public void testGrDefFieldsArePrivateInJavaCode() {
    myFixture.configureByText("X.groovy", "public class X{def x=5}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSuperConstructorInvocation() {doTest();}

  public void testDuplicateMapKeys() {doTest();}

  public void testIndexPropertyAccess() {doTest();}

  public void testPropertyAndFieldDeclaration() {doTest();}

  public void testGenericsMethodUsage() {doTest();}

  public void testWildcardInExtendsList() {doTest();}

  public void testOverrideAnnotation() {doTest();}

  public void testClosureCallWithTupleTypeArgument() {doTest();}

  public void testMethodDuplicates() {doTest();}

  public void testAmbiguousCodeBlock() {doTest();}
  public void testAmbiguousCodeBlockInMethodCall() {doTest();}
  public void testNotAmbiguousClosableBlock() {doTest();}
  public void testDuplicateParameterInClosableBlock() {doTest();}

  public void testCyclicInheritance() {doTest();}

  public void testNoDefaultConstructor() {doTest();}

  public void testNumberDuplicatesInMaps() {doTest();}

  public void testBuiltInTypeInstantiation() {doTest();}

  public void testSOEInFieldDeclarations() {doTest();}

  public void testVeryLongDfaWithComplexGenerics() {
    IdeaTestUtil.assertTiming("", 10000, 1, new Runnable() {
      @Override
      public void run() {
        doTest(new GroovyAssignabilityCheckInspection(), new UnusedDefInspection(), new GrUnusedIncDecInspection());
      }
    });
  }

  public void testWrongAnnotation() {doTest();}

  public void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java");
    doTest();
  }

  public void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSOFInDelegate() {
    doTest();
  }

  public void testMethodImplementedByDelegate() {
    doTest();
  }

  public void _testTestMarkupStubs() {
    doRefTest()
  }

  public void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy",
                              "List<? extends String> la = []; la.get(1); " +
                              "List<? super String> lb = []; lb.get(1); " +
                              "List<?> lc = []; lc.get(1); "
    );
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDuplicatedNamedArgs() {doTest();}

  public void testConstructorWithAllParametersOptional() {
    doTest();
  }

  public void testTupleConstructorAttributes() {
    doTest(new GroovyAssignabilityCheckInspection());
  }

  public void testDelegatedMethodIsImplemented() {
    doTest();
  }

  public void testEnumImplementsAllGroovyObjectMethods() {
    doTest();
  }

  public void _testBuilderMembersAreNotUnresolved() {
    doRefTest();
  }

  public void testRecursiveConstructors() {
    doTest();
  }

  public void testImmutableConstructorFromJava() {
    myFixture.addFileToProject "a.groovy", '''@groovy.transform.Immutable class Foo { int a; String b }'''
    myFixture.configureByText 'a.java', '''
class Bar {{
  new Foo<error>()</error>;
  new Foo<error>(2)</error>;
  new Foo(2, "3");
}}'''
    myFixture.checkHighlighting(false, false, false)
  }

  public void testTupleConstructorFromJava() {
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

  public void testInheritConstructorsFromJava() {
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

  void testDefaultInitializersAreNotAllowedInAbstractMethods() {doTest()}
  void testConstructorTypeArgs(){doTest()}

  void testIncorrectEscaping() {doTest()}
  void testExtendingOwnInner() {doTest()}

  void testRegexInCommandArg() {doTest()}

  public void testJUnitConvention() {
    myFixture.addClass("package junit.framework; public class TestCase {}")
    doTest(new JUnitTestClassNamingConventionInspection(), new JUnitAbstractTestClassNamingConventionInspection())
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
    System.out.print(new <error descr="'pack.Foo' has private access in 'pack'">Foo</error>());
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

  void testPrimitiveTypeParams() {
    myFixture.configureByText('a.groovy', '''\
List<<error descr="Primitive type parameters are not allowed in type parameter list">int</error>> list = new ArrayList<int><EOLError descr="'(' expected"></EOLError>
List<? extends <error descr="Primitive bound types are not allowed">double</error>> l = new ArrayList<double>()
List<?> list2
''')
    myFixture.testHighlighting(true, false, false)
  }

  public void testAliasInParameterType() {
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

  public void testReassignedHighlighting() {
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testInstanceOf() {
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

  public void testIncorrectTypeArguments(){
    myFixture.configureByText('_.groovy', '''\
class C <T extends String> {}
C<<error descr="Type parameter 'java.lang.Double' is not in its bound; should extend 'java.lang.String'">Double</error>> c
C<String> c2
C<error descr="Wrong number of type arguments: 2; required: 1"><String, Double></error> c3
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
catch (<warning descr="Exception 'java.lang.Throwable' has already been caught">e</warning>){}
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
   addCompileStatic();
   testHighlighting('''\
import groovy.transform.CompileStatic
@CompileStatic
int method(x, y, z) {
    if (x) {
        <error descr="Cannot assign 'String' to 'int'">'String'</error>
    } else if (y) {
        42
    }
    else if (z) {
      return <error descr="Cannot assign 'String' to 'int'">'abc'</error>
    }
    else {
      return 43
    }
}
''')
 }

  void testReassignedVarInClosure() {
    addCompileStatic()
    testHighlighting("""
$IMPORT_COMPILE_STATIC

@CompileStatic
test() {
    def var = "abc"
    def cl = {
        var = new Date()
    }
    cl()
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
  String b() default <error descr="Cannot assign 'ArrayList<String>' to 'String'">['a']</error>
  String[][] c() default <error descr="Cannot assign 'String' to 'String[][]'">'f'</error>
  String[][] d() default [['f']]
  String[][] e() default [[<error descr="Cannot assign 'ArrayList<String>' to 'String'">['f']</error>]]
}
''')
  }

  void testDelegateWithDeprecated() {
    testHighlighting('''\
interface Foo {
    @Deprecated
    void foo()
}


<error descr="Method 'foo' is not implemented">class FooImpl implements Foo</error> {
    @Delegate(deprecated = false) Foo delegate
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

  public void testSwitchInLoopNoSoe() {
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

A.B foo = new <error descr="Cannot reference nonstatic symbol 'A.B' from static context">A.B</error>()
''')
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
}