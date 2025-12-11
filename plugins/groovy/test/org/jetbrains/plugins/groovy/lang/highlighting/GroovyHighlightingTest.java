// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GroovyConstructorNamedArgumentsInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.jetbrains.plugins.groovy.transformations.TransformationUtilKt;

public class GroovyHighlightingTest extends GrHighlightingTestBase {
  public void testDuplicateClosurePrivateVariable() { doTest(); }

  public void testClosureRedefiningVariable() { doTest(); }

  public void testCircularInheritance() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
    doTest();
  }

  public void testEmptyTupleType() { doTest(); }

  public void testMapDeclaration() { doTest(); }

  public void testShouldNotImplementGroovyObjectMethods() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testJavaClassImplementingGroovyInterface() {
    addGroovyObject();
    myFixture.addFileToProject("Foo.groovy", "interface Foo {}");
    myFixture.testHighlighting(false, false, false, getTestName(false) + ".java");
  }

  public void testDuplicateFields() { doTest(); }

  public void testNoDuplicationThroughClosureBorder() {
    myFixture.addClass("package groovy.lang; public interface Closure {}");
    doTest();
  }

  public void testRecursiveMethodTypeInference() { doTest(); }

  public void testSuperClassNotExists() { doRefTest(); }

  public void testAnonymousClassConstructor() { doTest(); }

  public void testAnonymousClassAbstractMethod() { doTest(); }

  public void testAnonymousClassShouldImplementMethods() { doTest(); }

  public void testAnonymousClassShouldImplementSubstitutedMethod() { doTest(); }

  public void testUnresolvedLhsAssignment() { doRefTest(); }

  public void testUnresolvedAccess() { doRefTest(); }

  public void testBooleanProperties() { doRefTest(); }

  public void testDuplicateInnerClass() { doTest(); }

  public void testThisInStaticContext() { doTest(); }

  public void testLocalVariableInStaticContext() { doTest(); }

  public void testModifiersInPackageAndImportStatements() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy", "x/" + getTestName(false) + ".groovy");
    myFixture.testHighlighting(true, false, false, "x/" + getTestName(false) + ".groovy");
  }

  public void testBreakOutside() { doTest(); }

  public void testUndefinedLabel() { doTest(); }

  public void testNestedMethods() { doTest(); }

  public void testRawOverriddenMethod() { doTest(); }

  public void testFQNJavaClassesUsages() { doTest(); }

  public void testGstringAssignableToString() { doTest(); }

  public void testGstringAssignableToStringInClosureParameter() { doTest(); }

  public void testEachOverRange() { doTest(); }

  public void testEllipsisParam() {
    myFixture.configureByText("a.groovy", """
      class A {
        def foo(int... x){}
        def foo(int<error descr="Ellipsis type is not allowed here">...</error> x, double y) {}
      }
      """);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testStringAndGStringUpperBound() { doTest(); }

  public void testWithMethod() { doTest(); }

  public void testArrayLikeAccess() { doTest(); }

  public void testSetInitializing() { doTest(); }

  public void testEmptyTupleAssignability() { doTest(); }

  public void testGrDefFieldsArePrivateInJavaCode() {
    myFixture.configureByText("X.groovy", "public class X{def x=5}");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSuperConstructorInvocation() { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testDuplicateMapKeys() { doTest(); }

  public void testIndexPropertyAccess() { doTest(); }

  public void testPropertyAndFieldDeclaration() { doTest(); }

  public void testGenericsMethodUsage() { doTest(); }

  public void testWildcardInExtendsList() { doTest(); }

  public void testOverrideAnnotation() { doTest(); }

  public void testClosureCallWithTupleTypeArgument() { doTest(); }

  public void testMethodDuplicates() { doTest(); }

  public void testNotAmbiguousClosableBlock() { doTest(); }

  public void testDuplicateParameterInClosableBlock() { doTest(); }

  public void testCyclicInheritance() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
    doTest();
  }

  public void testNoDefaultConstructor() { doTest(); }

  public void testNumberDuplicatesInMaps() { doTest(); }

  public void testBuiltInTypeInstantiation() { doTest(); }

  public void testVoidArrayType() {
    doTestHighlighting("""
                         <error descr="Illegal type: 'void'">void</error>[] foo() {}
                         <error descr="Illegal type: 'void'">void</error>[] bar = foo()
                         """);
  }

  public void testSOEInFieldDeclarations() { doTest(); }

  public void testWrongAnnotation() { doTest(); }

  public void testAmbiguousMethods() {
    myFixture.copyFileToProject(getTestName(false) + ".java");
    doTest();
  }

  public void testGroovyEnumInJavaFile() {
    myFixture.copyFileToProject(getTestName(false) + ".groovy");
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testSOFInDelegate() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
    doTest();
  }

  public void testMethodImplementedByDelegate() {
    doTest();
  }

  public void testGdslWildcardTypes() {
    myFixture.configureByText("a.groovy", "List<? extends String> la = []; la.get(1); " +
                                          "List<? super String> lb = []; lb.get(1); " +
                                          "List<?> lc = []; lc.get(1); ");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDuplicatedNamedArgs() { doTest(); }

  public void testConstructorWithAllParametersOptional() { doTest(); }

  public void testTupleConstructorAttributes() { doTest(new GroovyAssignabilityCheckInspection()); }

  public void testDelegatedMethodIsImplemented() { doTest(); }

  public void testEnumImplementsAllGroovyObjectMethods() { doTest(); }

  public void testRecursiveConstructors() { doTest(); }

  public void testImmutableConstructorFromJava() {
    myFixture.addFileToProject("a.groovy", """
      @groovy.transform.Immutable class Foo { int a; String b }""");
    myFixture.configureByText("a.java", """
      class Bar {{
        new Foo<error>()</error>;
        new Foo<error>(2)</error>;
        new Foo(2, "3");
      }}""");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testTupleConstructorFromJava() {
    myFixture.addFileToProject("a.groovy", """
      @groovy.transform.TupleConstructor class Foo { int a; String b }""");
    myFixture.configureByText("a.java", """
      class Bar {{
        new Foo();
        new Foo(2);
        new Foo(2, "3");
        new Foo<error>(2, "3", 9)</error>;
      }}""");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testInheritConstructorsFromJava() {
    myFixture.addFileToProject("a.groovy", """
      class Person {
        Person(String first, String last) { }
        Person(String first, String last, String address) { }
        Person(String first, String last, int zip) { }
      }

      @groovy.transform.InheritConstructors
      class PersonAge extends Person {
        PersonAge(String first, String last, int zip) { }
      }
      """);
    myFixture.configureByText("a.java", """
      class Bar {{
        new PersonAge("a", "b");
        new PersonAge("a", "b", "c");
        new PersonAge("a", "b", 239);
        new PersonAge<error>(2, "3", 9)</error>;
      }}""");
    myFixture.checkHighlighting(false, false, false);
  }

  public void testDefaultInitializersAreNotAllowedInAbstractMethods() { doTest(); }

  public void testConstructorTypeArgs() { doTest(); }

  public void testIncorrectEscaping() { doTest(); }

  public void testExtendingOwnInner() { doTest(); }

  public void testDuplicateMethods() {
    myFixture.configureByText("a.groovy", """
      class A {
        <error descr="Method with signature foo() is already defined in the class 'A'">def foo()</error>{}
        <error descr="Method with signature foo() is already defined in the class 'A'">def foo(def a=null)</error>{}
      }
      """);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testPrivateTopLevelClassInJava() {
    myFixture.addFileToProject("pack/Foo.groovy", "package pack; private class Foo{}");
    myFixture.configureByText("Abc.java", """
      import pack.Foo;

      class Abc {
        void foo() {
          System.out.print(new Foo()); // top-level Groovy class can't be private
        }
      }
      """);

    myFixture.testHighlighting(false, false, false);
  }

  public void testDelegateToMethodWithItsOwnTypeParams() {
    myFixture.configureByText("a.groovy", """
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
      """);

    myFixture.testHighlighting(false, false, false);
  }

  public void testMethodDelegate() {
    myFixture.addClass("""
                          package groovy.lang;
                          @Target({ElementType.FIELD, ElementType.METHOD})
                          public @interface Delegate {}
                          """);
    myFixture.configureByText("a.groovy", """
      class A {
        def foo(){}
      }

      class B {
        @Delegate A getA(){return new A()}
      }

      new B().foo()
      """);

    getFixture().checkHighlighting();
  }

  public void testMethodDelegateError() {
    myFixture.configureByText("a.groovy", """
      class A {
        def foo(){}
      }

      class B {
        <error>@Delegate</error> A getA(int i){return new A()}
      }

      new B().foo()
      """);

    getFixture().checkHighlighting();
  }

  public void testBuilderSimpleStrategyError() {
    myFixture.addClass("""
                          package groovy.transform.builder;
                          @Target({ ElementType.TYPE})

                          public @interface Builder {
                            Class<?> builderStrategy();
                            boolean includeSuperProperties() default false;
                          }
                          """);
    myFixture.addClass("""
                          package groovy.transform.builder;
                          public class SimpleStrategy {}
                          """);

    myFixture.configureByText("a.groovy", """
      import groovy.transform.builder.Builder
      import groovy.transform.builder.SimpleStrategy

      <error>@Builder(builderStrategy = SimpleStrategy, includeSuperProperties = true)</error>
      class Pojo {
          String name
          def dynamic
          int counter

          def method() {}
      }
      """);

    getFixture().checkHighlighting();
  }

  public void testBuilderSimpleStrategy() {
    myFixture.addClass("""
                          package groovy.transform.builder;
                          @Target({ ElementType.TYPE})

                          public @interface Builder {
                            Class<?> builderStrategy();
                            boolean includeSuperProperties() default false;
                          }
                          """);

    myFixture.addClass("""
                          package groovy.transform.builder;
                          public class SimpleStrategy {}
                          """);

    myFixture.configureByText("a.groovy", """
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
      """);

    getFixture().checkHighlighting();
  }

  public void testPrimitiveTypeParams() {
    myFixture.configureByText("a.groovy", """
      List<<error descr="Primitive type parameters are not allowed in type parameter list">int</error>> list = new ArrayList<int><EOLError descr="'(' expected"></EOLError>
      List<? extends <error descr="Primitive bound types are not allowed">double</error>> l = new ArrayList<double>()
      List<?> list2
      """);
    myFixture.testHighlighting(true, false, false);
  }

  public void testAliasInParameterType() {
    myFixture.configureByText("a_.groovy", """
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
      """);
    myFixture.testHighlighting(true, false, false);
  }

  public void testReassignedHighlighting() {
    myFixture.testHighlighting(true, true, true, getTestName(false) + ".groovy");
  }

  public void testInstanceOf() {
    myFixture.configureByText("_a.groovy", """
      class DslPointcut {}

      private def handleImplicitBind(arg) {
          if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
              return DslPointcut.bind(arg)
          }
          return arg
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testIncorrectTypeArguments() {
    myFixture.configureByText("_.groovy", """
      class C <T extends String> {}
      C<<warning descr="Type parameter 'java.lang.Double' is not in its bound; should extend 'java.lang.String'">Double</warning>> c
      C<String> c2
      C<warning descr="Wrong number of type arguments: 2; required: 1"><String, Double></warning> c3
      """);
    myFixture.testHighlighting(true, false, true);
  }

  public void testTryCatch1() {
    doTestHighlighting("""
                         try {}
                         catch (Exception e){}
                         catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
                         """);
  }

  public void testTryCatch2() {
    doTestHighlighting("""
                         try {}
                         catch (e){}
                         catch (<warning descr="Exception 'java.lang.Exception' has already been caught">e</warning>){}
                         """);
  }

  public void testTryCatch3() {
    doTestHighlighting("""
                         try {}
                         catch (e){}
                         catch (<warning descr="Exception 'java.io.IOException' has already been caught">IOException</warning> e){}
                         """);
  }

  public void testTryCatch4() {
    doTestHighlighting("""
                         try {}
                         catch (Exception | <warning descr="Unnecessary exception 'java.io.IOException'. 'java.lang.Exception' is already declared">IOException</warning> e){}
                         """);
  }

  public void testTryCatch5() {
    doTestHighlighting("""
                         try {}
                         catch (RuntimeException | IOException e){}
                         catch (<warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
                         """);
  }

  public void testTryCatch6() {
    doTestHighlighting("""
                         try {}
                         catch (NullPointerException | IOException e){}
                         catch (ClassNotFoundException | <warning descr="Exception 'java.lang.NullPointerException' has already been caught">NullPointerException</warning> e){}
                         """);
  }

  public void testTryWithoutCatchFinally() { doTest(); }

  public void testVariableDeclarationTypeParameters() { doTest(); }

  public void testAnnotationFieldWithoutType() { doTest(); }

  public void testVariableDeclarationDuplicateModifiers() { doTest(); }

  public void testCompileStatic() {
    myFixture.addClass("""
                          package groovy.transform;
                          public @interface CompileStatic {
                          }""");

    doTestHighlighting("""
                         import groovy.transform.CompileStatic

                         class A {

                         def foo() {
                         print <warning descr="Cannot resolve symbol 'abc'">abc</warning>
                         }

                         @CompileStatic
                         def bar() {
                         print <error descr="Cannot resolve symbol 'abc'">abc</error>
                           new Object() {
                             def baz() {
                               print(<error descr="Cannot resolve symbol 'unknown'">unknown</error>)
                             }
                           }
                         }
                         }
                         """, true, false, false, GrUnresolvedAccessInspection.class);
  }

  public void testUnresolvedVarInStaticMethod() {
    doTestHighlighting("""
                         static def foo() {
                           print <error descr="Cannot resolve symbol 'abc'">abc</error>

                           def cl = {
                              print <warning descr="Cannot resolve symbol 'cde'">cde</warning>
                           }
                         }
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testStaticOkForClassMembersWithThisQualifier() {
    doTestHighlighting("""
                         class A {
                           def foo(){}
                           static bar() {
                             this.toString()
                             this.getFields()
                             this.<warning descr="Cannot reference non-static symbol 'foo' from static context">foo</warning>()
                           }
                         }
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testScriptFieldsAreAllowedOnlyInScriptBody() {
    addGroovyTransformField();
    doTestHighlighting("""
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
                         """);
  }

  public void testDuplicatedScriptField() {
    addGroovyTransformField();
    doTestHighlighting("""
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
                         """);
  }

  public void testReturnTypeInStaticallyCompiledMethod() {
    addCompileStatic();
    doTestHighlighting("""
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
                         """);
  }

  public void testOverrideForVars() {
    doTestHighlighting("""
                         class S {
                           @<error descr="'@Override' not applicable to field">Override</error> def foo;

                           def bar() {
                            @<error descr="'@Override' not applicable to local variable">Override</error> def x
                           }
                         }""");
  }

  public void testUnusedImportToList() {
    myFixture.addClass("""
                         package java.awt;
                         public class Component{}""");
    doTestHighlighting("""
                         import java.awt.Component
                         <warning descr="Unused import">import java.util.List</warning>

                         print Component
                         print List
                         """);
  }

  public void testUsedImportToList() {
    myFixture.addClass("""
                         package java.awt;
                         public class Component{}""");
    myFixture.addClass("""
                         package java.awt;
                         public class List{}""");
    myFixture.addClass("""
                         package java.util.concurrent;
                         public class ConcurrentHashMap{}""");
    doTestHighlighting("""
                         import java.awt.*
                         import java.util.List
                         <warning descr="Unused import">import java.util.concurrent.ConcurrentHashMap</warning>

                         print Component
                         print List
                         """);
  }

  public void testIncompatibleTypeOfImplicitGetter() {
    doTestHighlighting("""
                         abstract class Base {
                             abstract String getFoo()
                         }

                         class Inheritor extends Base {
                             final <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">foo</error> = '3'
                         }""");
  }

  public void testIncompatibleTypeOfInheritedMethod() {
    doTestHighlighting("""
                         abstract class Base {
                           abstract String getFoo()
                         }

                         class Inheritor extends Base {
                             def <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">getFoo</error>() {''}
                         }""");
  }

  public void testIncompatibleTypeOfInheritedMethod2() {
    doTestHighlighting("""
                         abstract class Base {
                           abstract String getFoo()
                         }

                         class Inheritor extends Base {
                             <error descr="The return type of java.lang.Object getFoo() in Inheritor is incompatible with java.lang.String getFoo() in Base">Object</error> getFoo() {''}
                         }""");
  }

  public void testIncompatibleTypeOfInheritedMethodInAnonymous() {
    doTestHighlighting("""
                         abstract class Base {
                           abstract String getFoo()
                         }

                         new Base() {
                             <error descr="The return type of java.lang.Object getFoo() in anonymous class derived from Base is incompatible with java.lang.String getFoo() in Base">Object</error> getFoo() {''}
                         }""");
  }

  public void testAnnotationArgs() {
    doTestHighlighting("""
                         @interface Int {
                           int value()
                           String s() default 'a'
                         }

                         @Int(<error descr="Cannot assign 'String' to 'int'">'a'</error>) def foo(){}

                         @Int(2) def bar(){}

                         @Int(value = 2) def c(){}

                         @Int(value = 3, s = <error descr="Cannot assign 'Integer' to 'String'">4</error>) def x(){}

                         @Int(value = 3, s = '4') def y(){}
                         """);
  }

  public void testDefaultAttributeValue() {
    doTestHighlighting("""
                         @interface Int {
                           int value1() default 2
                           String value2() default <error descr="Cannot assign 'Integer' to 'String'">2</error>
                           String value3()
                         }
                         """);
  }

  public void testAnnotationMethodThrowsList() {
    doTestHighlighting("""
                         @interface A {
                           int aaa() <error descr="'throws' clause is not allowed in @interface members">throws IOException</error>;
                           int aab() throws<error descr="<type> expected, got ';'"> </error>;
                         }
                         """);
  }

  public void testAnnotationAttributeTypes() {
    doTestHighlighting("""
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
                         """);
  }

  public void testDefaultAnnotationValue() {
    doTestHighlighting("""
                         @interface A {
                           int a() default 2
                           String b() default <error descr="Cannot assign 'List<String>' to 'String'">['a']</error>
                           String[][] c() default <error descr="Cannot assign 'String' to 'String[][]'">'f'</error>
                           String[][] d() default [['f']]
                           String[][] e() default [[<error descr="Cannot assign 'List<String>' to 'String'">['f']</error>]]
                         }
                         """);
  }

  public void testAbstractMethodWithBody() {
    doTestHighlighting("""
                         interface A {
                           def foo()<error descr="Interface abstract methods must not have body">{}</error>
                         
                           <error descr="Interface members are not allowed to be private">private</error> def privateMethod() <error descr="Interface abstract methods must not have body">{}</error>
                         
                           <error descr="Interface must have no static method">static</error> def staticMethod() <error descr="Interface abstract methods must not have body">{}</error>
                         
                           <error descr="Modifier 'default' is available in Groovy 3.0 or later">default</error> def defaultMethod() {}
                         }

                         abstract class B {
                           abstract foo()<error descr="Abstract methods must not have body">{}</error>
                         }

                         class X {
                           def foo(){}
                         }
                         """);
  }

  public void testTupleVariableDeclarationWithRecursion() {
    doTestHighlighting("def (a, b) = [a, a]");
  }

  public void testSwitchInLoopNoSoe() {
    doTestHighlighting("""
                         def foo(File f) {
                           while (true) {
                             switch (f.name) {
                               case 'foo': f = new File('bar')
                             }
                             if (f) {
                               return
                             }
                           }
                         }""");
  }

  public void testInstanceMethodUsedInStaticClosure() {
    doTestHighlighting("""
                         class A {
                             static staticClosure = {
                                 foo()
                             }

                             def staticMethod() {
                                 foo()
                             }

                             def foo() { }
                         }
                         """);
  }

  public void testStaticOk() {
    doTestHighlighting("""
                         class A {
                           class B {}
                         }

                         A.B foo = new A.B()
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testDuplicatedVar0() {
    doTestHighlighting("""
                         def a = 5
                         def <error descr="Variable 'a' already defined">a</error> = 7
                         """);
  }

  public void testDuplicatedVarInIf() {
    doTestHighlighting("""
                         def a = 5
                         if (cond)
                           def <error descr="Variable 'a' already defined">a</error> = 7
                         """);
  }

  public void testDuplicatedVarInAnonymous() {
    doTestHighlighting("""
                         def foo() {
                           def a = 5

                           new Runnable() {
                             void run() {
                               def <error descr="Variable 'a' already defined">a</error> = 7
                             }
                           }
                         }
                         """);
  }

  public void testDuplicatedVarInClosure() {
    doTestHighlighting("""
                         def foo() {
                           def a = 5

                           [1, 2, 3].each {
                             def <error descr="Variable 'a' already defined">a</error> = 7
                           }
                         }
                         """);
  }

  public void testDuplicatedVarInClosureParameter() {
    doTestHighlighting("""
                         def foo() {
                           def a = 5

                           [1, 2, 3].each {<error descr="Variable 'a' already defined">a</error> ->
                             print a
                           }
                         }
                         """);
  }

  public void testDuplicatedVarInAnonymousParameter() {
    doTestHighlighting("""
                         def a = -1

                         def foo() {
                           def a = 5

                           new Object() {
                             void bar(int <error descr="Variable 'a' already defined">a</error>) {}
                           }
                         }
                         """);
  }

  public void testNoDuplicateErrors() {
    doTestHighlighting("""
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
                         """);
  }

  public void testPropertySelectionMayBeLValue() {
    doTestHighlighting("""
                         def methodMissing(String methodName, args) {
                             def closure = {
                                 callSomeOtherMethodInstead()
                             }
                             this.metaClass."$methodName" = closure
                             <error descr="Invalid value to assign to">closure()</error> = 2
                         }
                         """);
  }

  public void testEnumExtendsList() {
    doTestHighlighting("""
                         enum Ee <error descr="Enums may not have 'extends' clause">extends Enum</error> {
                         }
                         """);
  }

  public void testVarInTupleDuplicate() {
    doTestHighlighting("""
                         def (a, b) = []
                         def (<error descr="Variable 'b' already defined">b</error>, c, <error descr="Variable 'c' already defined">c</error>) = []
                         """);
  }

  public void testCreateMethodFromUsageIsAvailableInStaticMethod() {
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    doTestHighlighting("""
                         class A {
                           static foo() {
                             <warning descr="Cannot resolve symbol 'abc'">a<caret>bc</warning>()
                           }
                         }
                         """);

    TestCase.assertNotNull(myFixture.findSingleIntention("Create method 'abc'"));
  }

  public void testTypeParameterIsCorrect() {
    doTestHighlighting("""
                         class Component{}

                         class Window extends Component{
                             Window(i) {
                             }
                         }

                         class Super <C extends Component> {

                         }

                         class Sub<W extends Window> extends Super<W> {
                         }
                         """);
  }

  public void testAnonymousBodyOnNewLineWithinParenthesizedExpression() {
    doTestHighlighting("""
                         class Foo {
                           def i
                         }

                         def foo = new Foo()
                         {
                         }

                         def bar = (new Foo()
                         {
                         })

                         def baz
                         baz = new Foo()
                         {
                         }

                         baz = (new Foo()
                         {
                         })

                         (baz = new Foo()
                         {
                         })

                         new Foo()
                         {
                         }

                         (new Foo()
                         {
                         })

                         new Foo()
                         {
                         } + 666

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
                         {
                         }.getI()

                         (new Foo()
                         {
                         }).getI()

                         (new Foo()
                         {
                         }.getI())

                         def mm() {
                             new Foo()
                             {
                             }
                         }

                         def mm2() {
                             (new Foo()
                             {
                             })
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
                                 {
                                 }
                             }
                         })
                         """);
  }

  public void testAnonymousBodyOnNewLineWithinArgumentList() {
    doTestHighlighting("""
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

                         foo 1 + (new Foo()
                         {
                         }) + 22

                         foo(new Foo() {
                             def a() {
                                 // still error
                                 new Foo()
                                 {

                                 }
                             }
                         })
                         """);
  }

  public void testGenerics() {
    addHashSet();
    doTestHighlighting("""
                         class NodeInfo{}

                         interface NodeEvent<T> {}

                         interface TrackerEventsListener<N extends NodeInfo, E extends NodeEvent<N>> {
                             void onEvents(Collection<E> events)
                         }

                         class AgentInfo extends NodeInfo {}

                         print new HashSet<TrackerEventsListener<AgentInfo, NodeEvent<AgentInfo>>>() //correct
                         print new HashSet<TrackerEventsListener<AgentInfo, <warning descr="Type parameter 'NodeEvent<java.lang.Object>' is not in its bound; should extend 'NodeEvent<N>'">NodeEvent<Object></warning>>>() //incorrect
                         """);
  }

  public void testTypeInConstructor() {
    doTestHighlighting("""
                         class X {
                           public <error descr="Return type element is not allowed in constructor">void</error> X() {}
                         }
                         """);
  }

  public void testFinalMethodOverriding() {
    doTestHighlighting("""
                         class A {
                             final void foo() {}
                         }

                         class B extends A{
                             <error descr="Method 'foo()' cannot override method 'foo()' in 'A'; overridden method is final">void foo()</error> {}
                         }
                         """);
  }

  public void testWeakerMethodAccess0() {
    doTestHighlighting("""
                         class A {
                             void foo() {}
                         }

                         class B extends A{
                             <error descr="Method 'foo()' cannot have weaker access privileges ('protected') than 'foo()' in 'A' ('public')">protected</error> void foo() {}
                         }
                         """);
  }

  public void testWeakerMethodAccess1() {
    doTestHighlighting("""
                         class A {
                             void foo() {}
                         }

                         class B extends A{
                             <error descr="Method 'foo()' cannot have weaker access privileges ('private') than 'foo()' in 'A' ('public')">private</error> void foo() {}
                         }
                         """);
  }

  public void testWeakerMethodAccess2() {
    doTestHighlighting("""
                         class A {
                             public void foo() {}
                         }

                         class B extends A{
                             void foo() {} //don't highlight anything
                         }
                         """);
  }

  public void testWeakerMethodAccess3() {
    doTestHighlighting("""
                         class A {
                             protected void foo() {}
                         }

                         class B extends A{
                             <error descr="Method 'foo()' cannot have weaker access privileges ('private') than 'foo()' in 'A' ('protected')">private</error> void foo() {}
                         }
                         """);
  }

  public void testOverriddenProperty() {
    doTestHighlighting("""
                         class A {
                             final foo = 2
                         }

                         class B extends A {
                             <error descr="Method 'getFoo()' cannot override method 'getFoo()' in 'A'; overridden method is final">def getFoo()</error>{5}
                         }
                         """);
  }

  public void testUnresolvedQualifierHighlighting() {
    doTestHighlighting("""
                         <error descr="Cannot resolve symbol 'Abc'">Abc</error>.<error descr="Cannot resolve symbol 'Cde'">Cde</error> abc
                         """);
  }

  public void testVarargParameterWithoutTypeElement() {
    doTestHighlighting("def foo(def <error descr=\"Ellipsis type is not allowed here\">...</error> vararg, def last) {}");
  }

  public void testTupleInstanceCreatingInDefaultConstructor() {
    doTestHighlighting("""
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
                         """);
  }

  public void testArrayAccessForMapProperty() {
    doTestHighlighting("""
                         def bar() {
                             return [list:[1, 2, 3]]
                         }

                         def testConfig = bar()
                         print testConfig.list[0]
                         print <weak_warning descr="Cannot infer argument types">testConfig.foo<warning descr="'foo' cannot be applied to '()'">()</warning></weak_warning>
                         """, true, false, true, GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  public void testGStringInjectionLFs() {
    doTestHighlighting("""
                         print "${<error descr="GString injection must not contain line feeds">
                         </error>}"

                         print ""\"${
                         }""\"

                         print "${ ""\"
                         ""\" }"

                         print "${"\\
                         hi"}"

                         print "${<error descr="GString injection must not contain line feeds">
                           </error>1 + 2
                         }"

                         print "${1 + <error descr="GString injection must not contain line feeds">
                           </error>2
                         }"

                         print "${1 + 2<error descr="GString injection must not contain line feeds">
                         </error>}"
                         """);
  }

  public void testListOrMapErrors() {
    doTestHighlighting("""
                         print([1])
                         print([1:2])
                         print(<error descr="Collection literal contains named and expression arguments at the same time">[1:2, 4]</error>)
                         """);
  }

  public void _testDelegatesToApplicability() {
    doTestHighlighting("""
                         def with(@DelegatesTo.Target Object target, @DelegatesTo Closure arg) {
                           arg.delegate = target
                           arg()
                         }

                         def with2(Object target, @<error descr="Missed attributes: value">DelegatesTo</error> Closure arg) {
                           arg.delegate = target
                           arg()
                         }
                         """);
  }

  public void testClosureParameterInferenceDoesNotWorkIfComplieStatic() {
    addCompileStatic();
    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         def foo() {
                             final collector = [1, 2].find {a ->
                                 a.<error descr="Cannot resolve symbol 'intValue'">intValue</error>()
                             }
                         }
                         """);
  }

  public void testIllegalLiteralName() {
    doTestHighlighting("""
                         def <error descr="Illegal escape character in string literal">'a\\obc'</error>() {

                         }
                         """);
  }

  public void testExceptionParameterAlreadyDeclared() {
    doTestHighlighting("""
                         int missing() {
                           InputStream i = null

                           try {
                             return 1
                           }
                           catch(Exception <error descr="Variable 'i' already defined">i</error>) {
                             return 2
                           }
                         }
                         """);
  }

  public void testInnerAnnotationType() {
    doTestHighlighting("""
                         class A {
                           @interface <error descr="Annotation type cannot be inner">X</error> {}
                         }
                         """);
  }

  public void testDuplicatingAnnotations() {
    doTestHighlighting("""
                         @interface A {
                           String value()
                         }

                         @A('a')
                         @A('a')
                         class X{}

                         @A('a')
                         @A('ab')
                         class Y{}

                         public <error descr="Duplicate modifier 'public'">public</error> class Z {}
                         """);
  }

  public void testAnnotationAttribute() {
    doTestHighlighting("""
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
                         """);
  }

  public void testDuplicateMethodsWithGenerics() {
    doTestHighlighting("""
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
                         }""");
  }

  public void testOverriddenReturnType0() {
    myFixture.addClass("class Base{}");
    myFixture.addClass("class Inh extends Base{}");
    doTestHighlighting("""
                         class A {
                           List<Base> foo() {}
                         }

                         class B extends A {
                           List<Inh> foo() {} //correct
                         }
                         """);
  }

  public void testOverriddenReturnType1() {
    myFixture.addClass("class Base extends SuperBase {}");
    myFixture.addClass("class Inh extends Base{}");
    doTestHighlighting("""
                         class A {
                           List<Base> foo() {}
                         }

                         class B extends A {
                           <error>Collection<Base></error> foo() {}
                         }
                         """);
  }

  public void testOverriddenReturnType2() {
    myFixture.addClass("class Base extends SuperBase {}");
    myFixture.addClass("class Inh extends Base{}");
    doTestHighlighting("""
                         class A {
                           List<Base> foo() {}
                         }

                         class B extends A {
                           <error>int</error> foo() {}
                         }
                         """);
  }

  public void testOverriddenReturnType3() {
    myFixture.addClass("class Base extends SuperBase {}");
    myFixture.addClass("class Inh extends Base{}");
    doTestHighlighting("""
                         class A {
                           Base[] foo() {}
                         }

                         class B extends A {
                           <error>Inh[]</error> foo() {}
                         }
                         """);
  }

  public void testOverriddenReturnType4() {
    myFixture.addClass("class Base extends SuperBase {}");
    myFixture.addClass("class Inh extends Base{}");
    doTestHighlighting("""
                         class A {
                           Base[] foo() {}
                         }

                         class B extends A {
                           Base[] foo() {}
                         }
                         """);
  }

  public void testEnumConstantAsAnnotationAttribute() {
    doTestHighlighting("""
                         enum A {CONST}

                         @interface I {
                             A foo()
                         }

                         @I(foo = A.CONST) //no error
                         def bar
                         """);
  }

  public void testUnassignedFieldAsAnnotationAttribute() {
    doTestHighlighting("""
                         interface A {
                           String CONST
                         }

                         @interface I {
                             String foo()
                         }

                         @I(foo = <error descr="Expected 'A.CONST' to be an inline constant">A.CONST</error>)
                         def bar
                         """);
  }

  public void testFinalFieldRewrite() {
    doTestHighlighting("""
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
                         """);
  }

  public void testStaticFinalFieldRewrite() {
    doTestHighlighting("""
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
                         """);
  }

  public void testSOEIfExtendsItself() {
    TransformationUtilKt.disableAssertOnRecursion(getTestRootDisposable());
    doTestHighlighting("""
                         <error descr="Cyclic inheritance involving 'A'">class A extends A</error> {
                           def foo
                         }

                         <error descr="Cyclic inheritance involving 'B'">class B extends C</error> {
                           def foo
                         }

                         <error descr="Cyclic inheritance involving 'C'">class C extends B</error> {
                         }
                         """);
  }

  public void testFinalParameter() {
    doTestHighlighting("""
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
                         """);
  }

  public void testNonStaticInnerClass1() {
    doTestHighlighting("""
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
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testNonStaticInnerClass2() {
    doTestHighlighting("""
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
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testNonStaticInnerClass3() {
    myFixture.configureByText("_.groovy", """
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
      """);

    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.testHighlighting(true, false, true);
  }

  public void testNonStaticInnerClass4() {
    myFixture.configureByText("_.groovy", """
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
      """);

    myFixture.enableInspections(GrUnresolvedAccessInspection.class);
    myFixture.testHighlighting(true, false, true);
  }

  public void testInnerClassWithStaticMethod() {
    doTestHighlighting("""
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
                         """);
  }

  public void testUnresolvedPropertyWhenGetPropertyDeclared() {
    GrUnresolvedAccessInspection inspection = new GrUnresolvedAccessInspection();
    inspection.myHighlightIfGroovyObjectOverridden = false;
    myFixture.enableInspections(inspection);
    myFixture.configureByText("_.groovy", """
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
      """);
    myFixture.testHighlighting(true, false, true);
  }

  public void testImplementInaccessibleAbstractMethod() {
    myFixture.addClass("""
                          package p;

                          public abstract class Base {
                            abstract void foo();
                          }
                          """);
    doTestHighlighting("""
                         <error>class Foo extends p.Base</error> {
                         }
                         """);
  }

  public void testInjectedLiterals() {
    doTestHighlighting("""
                         //language=Groovy
                         def groovy1 = '''print 'abc\\' '''

                         //language=Groovy
                         def groovy2 = '''print <error descr="String end expected">'abc\\\\' </error>'''

                         """);
  }

  public void testAnnotationAsAnnotationValue() {
    doTestHighlighting("""
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
                         """);
  }

  public void testSameNameMethodWithDifferentAccessModifiers() {
    doTestHighlighting("""
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
                         """);
  }

  public void testImmutable() {
    doTestHighlighting("""
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
                         """);
  }

  public void testConstructorInImmutable() {
    doTestHighlighting("""
                         import groovy.transform.Immutable

                         @Immutable
                         class A {
                           String immutable
                           private String mutable

                           def <error descr="Explicit constructors are not allowed for @Immutable class">A</error>() {}
                         }
                         """);
  }

  public void testGetterInImmutable() {
    doTestHighlighting("""
                         import groovy.transform.Immutable

                         @Immutable
                         class A {
                           String immutable
                           private String mutable

                           String <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {immutable}
                           String getMutable() {mutable}
                         }
                         """);
  }

  public void testGetterInImmutable2() {
    doTestHighlighting("""
                         import groovy.transform.Immutable

                         @Immutable
                         class A {
                           String immutable

                           int <error descr="Repetitive method name 'getImmutable'">getImmutable</error>() {1}
                         }
                         """);
  }

  public void testMinusInAnnotationArg() {
    doTestHighlighting("""
                         @interface Xx {
                             int value()
                         }

                         @Xx(-1)
                         public class Bar1 { }

                         @Xx(+1)
                         public class Bar2 { }

                         @Xx(<error descr="Expected '++1' to be an inline constant">++1</error>)
                         public class Bar3 { }
                         """);
  }

  public void testImportStaticFix() {
    myFixture.configureByText("a.groovy", """
      class A {
        static void foo(String s){}
      }

      foo(<caret>)
      """);

    myFixture.getAvailableIntention("Static import method 'A.foo'");
  }

  public void testNoSOEInIndexPropertyAssignmentWithGenericFunction() {
    doTestHighlighting("""
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
                         """);
    doTestHighlighting("""
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
                         """);
  }

  public void testStaticGDKMethodApplicable() {
    doTestHighlighting("""
                         def doParse() {
                           Date.parse('dd.MM.yyyy', '01.12.2016')
                         }
                         """, GroovyAssignabilityCheckInspection.class);
  }

  public void testUnresolvedAnonymousBaseClass() {
    doTestHighlighting("def foo = new <error descr=\"Cannot resolve symbol 'Rrrrrrrr'\">Rrrrrrrr</error>() {}");
  }

  public void testLocalVariableModifiers() { doTest(); }

  public void testFieldModifiers() { doTest(); }

  public void testScriptFieldModifiers() { doTest(); }

  public void testAllowAndDoNotHighlightWithinPackage() {
    myFixture.addClass("""
                          package a.b.c.trait.d.as.e.def.f.in.g;
                          public class Foo {}\s
                          """);
    doTestHighlighting("import a.b.c.<info>trait</info>.d.<info>as</info>.e.<info>def</info>.f.<info>in</info>.g.*", false, true);
  }

  public void testResolveMethodsOfBoxedTypesOnPrimitiveQualifiers() {
    doTestHighlighting("""
                         class Widget {
                             float width = 1.1f
                         }

                         Widget w = new Widget()
                         w.width.round()
                         w.width.intValue()
                         w.width.compareTo(2f)
                         """, GrUnresolvedAccessInspection.class);
  }

  public void testNoWarningOnExtensionMethodWithSpreadOperator() {
    doTestHighlighting("[1, 2, 3]*.multiply(4)", GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  public void testTypeArgumentsInImportReferences() {
    doTestHighlighting("""
                         import java.util.List<error descr="Type argument list is not allowed here"><String></error>
                         import java.util.Map<error descr="Type argument list is not allowed here"><Integer, String></error>.Entry
                         import static java.util.Map<error descr="Type argument list is not allowed here"><Integer, String></error>.*
                         import java.util.List<error descr="Type argument list is not allowed here"><String></error> as Foo
                         """, false);
  }

  public void testAssignCollectionToAnArrayInCS() {
    doTestHighlighting("""
                         Collection<? extends Runnable> foo() {}

                         @groovy.transform.CompileStatic
                         def usage() {
                           Runnable[] ar = foo()
                         }

                         @groovy.transform.CompileStatic
                         def usage(Collection<? extends Runnable> cr) {
                           Runnable[] ar = cr //https://issues.apache.org/jira/browse/GROOVY-8983
                         }
                         """);
  }

  public void testMapConstructorWithMapLiteral() {
    doTestHighlighting("""
                         class A {
                           String s
                         }

                         new A([s : "abc", <warning>q</warning>: 1])
                         """, GroovyConstructorNamedArgumentsInspection.class);
  }

  public void testMapConstructorWithFinalField() {
    doTestHighlighting("""
                         class A {
                           final String s
                         }

                         new A([<warning>s</warning> : "abc"])
                         """, GroovyConstructorNamedArgumentsInspection.class);
  }

  public void testMapConstructorWithRawMapAssignment() {
    doTestHighlighting("""
                         class A {
                           final String s
                         }

                         A a = [<warning>s</warning> : "asd"]
                         """, GroovyConstructorNamedArgumentsInspection.class);
  }

  public void testIllegalMethodName() { doTest(); }

  public void testMethodReferenceWithJustTypeArgument() {
    doTestHighlighting("""
                         @groovy.transform.CompileStatic
                         class A {
                           String key = ""
                           String foo(int x) {return key}
                          \s
                           public static void main() {
                             def ref = A.&foo
                             A a = new A()
                             ref(a, 1)
                           }
                         }
                         """);
  }

  public void testVariableInAnonymousClassConstructor() {
    doTestHighlighting("""
                         class Foo {
                             int y;

                             Foo(int x) { this.y = x }

                             static void test() {
                                 int x = 5
                                 Foo foo = new Foo(x) {}
                                 println foo
                             }
                         }
                         """, UnusedDefInspection.class);
  }

  public void testClosureWithNullMapping() {
    doTestHighlighting("""
                         class A {
                             static def cl = {}
                         }

                         A.cl()""", GroovyAssignabilityCheckInspection.class);
  }

  public void testFieldClosure() {
    doTestHighlighting("""
                         class A {
                           Closure<?> f
                          \s
                           def foo() {
                             f()
                           }
                         }""");
  }

  public void testIDEA_280481() {
    doTestHighlighting("""
                         def output = ''
                         for (something5 in something4) {
                           [].eachLine { line ->
                               if (line) {
                                   def (line_number) = line
                                   output = output + line_number
                               }
                           }
                           output = "${output}"\s
                         }
                         new File('').withWriter('utf-8') {
                             writer -> writer.write(output)
                         }
                         """);
  }

  public void testIDEA_291580() {
    doTestHighlighting("<error>class Foo extends Foo</error> {}");
  }

  public void testLegalColonInName() {
    doTestHighlighting("def \"foo:bar\"() {}");
  }
}
