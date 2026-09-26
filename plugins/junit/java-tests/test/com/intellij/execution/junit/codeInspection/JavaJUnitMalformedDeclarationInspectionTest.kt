// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitMalformedDeclarationInspectionTestBase
import com.intellij.junit.testFramework.MavenTestLib.JUNIT3
import com.intellij.junit.testFramework.MavenTestLib.JUNIT4
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5
import com.intellij.junit.testFramework.MavenTestLib.JUNIT5_7_0
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.intellij.lang.annotations.Language
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
class JavaJUnitMalformedDeclarationInspectionTest {
  class V57 : JUnitMalformedDeclarationInspectionTestBase(JUNIT5_7_0) {
    fun `test malformed private highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        private Rule5 <warning descr="Field 'myRule5' annotated with '@RegisterExtension' should be public">myRule5</warning> = new Rule5();
        class Rule5 implements org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
    }

    fun `test malformed empty source highlighting with hashSet`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        class MyTest {
          @org.junit.jupiter.params.ParameterizedTest
          <warning descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'HashSet<String>' type">@org.junit.jupiter.params.provider.EmptySource</warning>
          void testZeroArgSet(java.util.HashSet<String> input) { }     
        }
      """.trimIndent())
    }
  }

  class Latest : JUnitMalformedDeclarationInspectionTestBase(JUNIT3, JUNIT4, JUNIT5) {
    /* Malformed extensions */
    fun `test nested method source annotation no highlighting`() {
      myFixture.addClass("""
        package org.example;
        
        import org.junit.jupiter.params.ParameterizedTest;
        import org.junit.jupiter.params.provider.MethodSource;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        
        @ParameterizedTest(name = "jdk {0}")
        @MethodSource("allJdks")
        @Retention(RetentionPolicy.RUNTIME)
        @interface TestAllJdks {}
      """.trimIndent())

      myFixture.addClass("""
        package org.example;
        
        import org.example.TestAllJdks;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        
        @TestAllJdks
        @Retention(RetentionPolicy.RUNTIME)
        public @interface TestInterface {}
      """.trimIndent())

      @Language("JAVA") val text = """
        import org.example.TestInterface;
        import org.junit.jupiter.params.provider.Arguments;
        import java.util.stream.Stream;

        class MyTests {
            public static Stream<Arguments> allJdks() {
                return Stream.of(Arguments.of(8), Arguments.of(11), Arguments.of(17));
            }

            @TestInterface
            public void my(int jdk) {
                System.out.println("testing with " + jdk);
            }
        }
      """.trimIndent()
      myFixture.testHighlighting(JvmLanguage.JAVA, text)
    }

    fun `test nested field source annotation no highlighting`() {
      myFixture.addClass("""
        package org.example;
        
        import org.junit.jupiter.params.ParameterizedTest;
        import org.junit.jupiter.params.provider.FieldSource;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        
        @ParameterizedTest(name = "jdk {0}")
        @FieldSource("allJdks")
        @Retention(RetentionPolicy.RUNTIME)
        @interface TestAllJdks {}
      """.trimIndent())

      myFixture.addClass("""
        package org.example;
        
        import org.example.TestAllJdks;
        import java.lang.annotation.Retention;
        import java.lang.annotation.RetentionPolicy;
        
        @TestAllJdks
        @Retention(RetentionPolicy.RUNTIME)
        public @interface TestInterface {}
      """.trimIndent())

      @Language("JAVA") val text = """
        import org.example.TestInterface;
        import java.util.Collection;
        import java.util.Arrays;

        class MyTests {
            public static final Collection<Object> allJdks = Arrays.asList(8, 11, 17);

            @TestInterface
            public void my(int jdk) {
                System.out.println("testing with " + jdk);
            }
        }
      """.trimIndent()
      myFixture.testHighlighting(JvmLanguage.JAVA, text)
    }

    fun `test malformed extension no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        Rule5 myRule5 = new Rule5();
        class Rule5 implements org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
    }

    fun `test malformed extension subtype highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        Rule5 <warning descr="Field 'myRule5' annotated with '@RegisterExtension' should be of type 'org.junit.jupiter.api.extension.Extension'">myRule5</warning> = new Rule5();
        class Rule5 { }
      }
    """.trimIndent())
    }

    fun `test malformed callback extension before all only highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        MyExt <warning descr="MyExt should be registered at the class level">ext</warning> = new MyExt();
        
        static class MyExt implements org.junit.jupiter.api.extension.BeforeAllCallback {
          public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
        }
      }
    """.trimIndent())
    }

    fun `test malformed callback extension before all only no highlighting when per class`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        MyExt ext = new MyExt();
        
        static class MyExt implements org.junit.jupiter.api.extension.BeforeAllCallback {
          public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
        }
      }
    """.trimIndent())
    }

    fun `test malformed callback extension dual mode weak warning`() {
      myFixture.configureByText("A.java", """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        MyExt <info descr="MyExt implements both class-level and instance-level callbacks; 'beforeAll' will not be called for a non-static field">ext</info> = new MyExt();

        static class MyExt implements org.junit.jupiter.api.extension.BeforeAllCallback,
                                org.junit.jupiter.api.extension.BeforeEachCallback {
          public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
          public void beforeEach(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
        }
      }
    """.trimIndent())
      myFixture.checkHighlighting(true, true, true)
    }

    fun `test malformed callback extension dual mode no highlighting when per class`() {
      myFixture.configureByText("A.java", """
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        MyExt ext = new MyExt();

        static class MyExt implements org.junit.jupiter.api.extension.BeforeAllCallback,
                                org.junit.jupiter.api.extension.BeforeEachCallback {
          public void beforeAll(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
          public void beforeEach(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
        }
      }
    """.trimIndent())
      myFixture.checkHighlighting(true, true, true)
    }

    fun `test malformed callback extension before each static no highlighting`() {
      myFixture.configureByText("A.java", """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        static MyExt ext = new MyExt();

        static class MyExt implements org.junit.jupiter.api.extension.BeforeEachCallback {
          public void beforeEach(org.junit.jupiter.api.extension.ExtensionContext ctx) {}
        }
      }
    """.trimIndent())
      myFixture.checkHighlighting(true, true, true)
    }

    /* Malformed nested class */
    fun `test malformed nested class no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
      }
    """.trimIndent())
    }

    fun `test abstract class with inherited jupiter test no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      abstract class AbstractTest {
        @org.junit.jupiter.api.BeforeEach
        public void setUp() {}

        public abstract void testMethod();
      }

      class ConcreteTest extends AbstractTest {
        @org.junit.jupiter.api.Test
        @Override
        public void testMethod() {}
      }
      """.trimIndent())
    }

    fun `test malformed nested class highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class <warning descr="Tests in nested class will not be executed">B</warning> { }
        
        @org.junit.jupiter.api.Nested
        private class <warning descr="Tests in nested class will not be executed">C</warning> { }
        
        @org.junit.jupiter.api.Nested
        private static class <warning descr="Tests in nested class will not be executed">D</warning> { }
        
        public static class E {
          @org.junit.jupiter.api.Test
          public void testFoo() { }
        }
      }
    """.trimIndent())
    }

    fun `test malformed nested class quickfix`() {
      myFixture.testAllQuickfixes(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class B { }
        
        @org.junit.jupiter.api.Nested
        private class C { }
        
        @org.junit.jupiter.api.Nested
        private static class D { }
      }
    """.trimIndent(), """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
        
        @org.junit.jupiter.api.Nested
        public class C { }
        
        @org.junit.jupiter.api.Nested
        public class D { }
      }
    """.trimIndent(), "Fix class signature")
    }

    fun `test malformed nested class preview`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class <caret>B { }
      }
    """.trimIndent(), """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
      }
    """.trimIndent(), "Fix 'B' class signature", testPreview = true)
    }

    fun `test highlighting non executable JUnit 4 nested class`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A { 
        public class <warning descr="Tests in nested class will not be executed">B</warning> { 
          @org.junit.Test
          public void testFoo() { }
        }
      }  
    """.trimIndent())
    }

    fun `test highlighting executable JUnit 4 static inner class`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A { 
        public static class B { 
          @org.junit.Test
          public void testFoo() { }
        }
      }  
    """.trimIndent())
    }

    fun `test quickfix no nested annotation in JUnit 4`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """ 
      class A {
          public class <caret>B { 
              @org.junit.Test
              public void testFoo() { }
          }
      }
    """.trimIndent(), """ 
      import org.junit.experimental.runners.Enclosed;
      import org.junit.runner.RunWith;
      
      @RunWith(Enclosed.class)
      class A {
          public static class B { 
              @org.junit.Test
              public void testFoo() { }
          }
      }
    """.trimIndent(), "Fix class signatures", testPreview = true)
    }

    fun `test highlighting no nested annotation in JUnit 5`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        class <warning descr="Tests in nested class will not be executed">B</warning> { 
          @org.junit.jupiter.api.Test
          public void testFoo() { }
        }
      }  
    """.trimIndent())
    }

    fun `test quickfix no nested annotation in JUnit 5`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class A {
          class B<caret> { 
              @org.junit.jupiter.api.Test
              public void testFoo() { }
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Nested;
      
      class A {
          @Nested
          class B { 
              @org.junit.jupiter.api.Test
              public void testFoo() { }
          }
      }
    """.trimIndent(), hint = "Fix 'B' class signature", testPreview = true)
    }

    fun `test malformed parameterized class no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        enum TestEnum { FIRST, SECOND, THIRD }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        class ValueSourcesTest {
          @org.junit.jupiter.params.Parameter
          int i;
  
          @org.junit.jupiter.api.Test
          void testWithIntValues() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(longs = {1L})
        class LongValueSourcesTest {
          @org.junit.jupiter.params.Parameter
          long i;
  
          @org.junit.jupiter.api.Test
          void testWithLongValues() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(doubles = {0.5})
        class DoubleValueSourcesTest {
          @org.junit.jupiter.params.Parameter
          double d;
  
          @org.junit.jupiter.api.Test
          void testWithDoubleValues() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = {""})
        class StringValueSourcesTest {
          @org.junit.jupiter.params.Parameter
          String s;
  
          @org.junit.jupiter.api.Test
          void testWithStringValues() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        class ImplicitParameterTest {
          @org.junit.jupiter.params.Parameter
          String argument;
  
          @org.junit.jupiter.api.Test
          void implicitParameter(org.junit.jupiter.api.TestInfo testReporter) { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = { "FIRST" })
        class ImplicitConversionEnumTest {
          @org.junit.jupiter.params.Parameter
          TestEnum e;
  
          @org.junit.jupiter.api.Test
          void implicitConversionEnum() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = { "1" })
        class ImplicitConversionStringTest {
          @org.junit.jupiter.params.Parameter
          int i;
  
          @org.junit.jupiter.api.Test
          void implicitConversionString() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = "title")
        class ImplicitConversionClassTest {
          @org.junit.jupiter.params.Parameter
          Book book;
  
          @org.junit.jupiter.api.Test
          void implicitConversionClass() { }
  
          static class Book { public Book(String title) { } }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource("stream")
        class MethodSourceTest {
          @org.junit.jupiter.params.Parameter(0)
          int x;
  
          @org.junit.jupiter.params.Parameter(1)
          int y;
  
          @org.junit.jupiter.api.Test
          void simpleStream() { System.out.println(x + ", " + y); }
  
          static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stream() { 
            return java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of(1, 2)); 
          }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource("iterable")
        class IterableMethodSourceTest {
          @org.junit.jupiter.params.Parameter(0)
          int x;
  
          @org.junit.jupiter.params.Parameter(1)
          int y;
  
          @org.junit.jupiter.api.Test
          void simpleIterable() { System.out.println(x + ", " + y); }
  
          static Iterable<org.junit.jupiter.params.provider.Arguments> iterable() { 
            return java.util.Arrays.asList(org.junit.jupiter.params.provider.Arguments.of(1, 2)); 
          }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.EnumSource(names = "FIRST")
        class EnumSourceTest {
          @org.junit.jupiter.params.Parameter
          TestEnum value;
  
          @org.junit.jupiter.api.Test
          void runTest() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.EnumSource(
          value = TestEnum.class,
          names = "regexp-value",
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.MATCH_ALL
        )
        class EnumSourceModeTest {
          @org.junit.jupiter.params.Parameter
          TestEnum value;
  
          @org.junit.jupiter.api.Test
          void disable() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.CsvSource(value = "src, 1")
        class CsvSourceTest {
          @org.junit.jupiter.params.Parameter(0)
          String first;
  
          @org.junit.jupiter.params.Parameter(1)
          int second;
  
          @org.junit.jupiter.api.Test
          void testWithCsvSource() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.CsvSource(value = "src, 1")
        class CsvSourceTestWithConstructor {
          public CsvSourceTestWithConstructor() {}
          public CsvSourceTestWithConstructor(String first, int second) { }
  
          @org.junit.jupiter.api.Test
          void testWithCsvSource() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.NullSource
        class NullSourceTest {
          @org.junit.jupiter.params.Parameter
          Object o;
  
          @org.junit.jupiter.api.Test
          void testWithNullSrc() { }
        }
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.NullSource
        class TestWithNullSrcNoParam {
          public TestWithNullSrcNoParam(String str) {}
          @org.junit.jupiter.api.Test
          void test() {}
        }
        
        class OuterClass {
          @org.junit.jupiter.api.Nested
          @org.junit.jupiter.params.ParameterizedClass
          @org.junit.jupiter.params.provider.NullSource
          class NestedClassTest {
            @org.junit.jupiter.params.Parameter
            Object o;
          
            @org.junit.jupiter.api.Test
            void test() { }
          }
          
          @org.junit.jupiter.params.ParameterizedClass
          @org.junit.jupiter.params.provider.NullSource
          static class ClassStaticTest {
            @org.junit.jupiter.params.Parameter
            Object o;
          
            @org.junit.jupiter.api.Test
            void test() { }
          }
        }
        """.trimIndent()
      )
    }

    fun `test malformed inherited parameterized class`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        
        @org.junit.jupiter.params.ParameterizedClass
        abstract class ParentTest {
        }
        
        @org.junit.jupiter.params.provider.ValueSource(strings = { "1" })
        class OkChildTest extends ParentTest {
          @org.junit.jupiter.params.Parameter
          String value;
        
          @org.junit.jupiter.api.Test
          void test() { }
        }
        
        class <warning descr="No sources are provided, the suite would be empty">FailChildTest</warning> extends ParentTest {
          @org.junit.jupiter.api.Test
          void test() { }
        }
      """.trimIndent())
    }

    fun `test malformed inherited source for parameterized class`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(strings = { "1" })
        abstract class ParentTest {
        }
        
        class OkChildTest extends ParentTest {
          @org.junit.jupiter.params.Parameter
          String value;
        
          @org.junit.jupiter.api.Test
          void test() { }
        }
      """.trimIndent())
    }

    fun `test malformed parameterized inner class should be nested highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        class OuterClass {
          @org.junit.jupiter.params.ParameterizedClass
          @org.junit.jupiter.params.provider.ValueSource(booleans = {false})
          class <warning descr="Tests in nested class will not be executed">TestInnerClass</warning> {
            @org.junit.jupiter.params.Parameter
            boolean argument;
          
            @org.junit.jupiter.api.Test
            void test() { }
          }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class must specify a method name when using MethodSource`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.<warning descr="You must specify a method name when using @MethodSource with @ParameterizedClass">MethodSource</warning>
        class TestInnerClass {        
          @org.junit.jupiter.api.Test
          void test() { }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized inner class should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        class OuterClass {
          @org.junit.jupiter.api.Nested
          @org.junit.jupiter.params.ParameterizedClass
          @org.junit.jupiter.params.provider.ValueSource(booleans = {false})
          static class <warning descr="Tests in nested class will not be executed">TestInnerClass</warning> {
            @org.junit.jupiter.params.Parameter
            boolean argument;
          
            @org.junit.jupiter.api.Test
            void test() { }
          }
        }
        """.trimIndent())
    }


    fun `test malformed parameterized class value source wrong type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.ValueSource(booleans = {
          <warning descr="No implicit conversion found to convert 'boolean' to 'int'">false</warning>
        })
        class TestWithBooleanSource {
          @org.junit.jupiter.params.Parameter
          int argument;
        
          @org.junit.jupiter.api.Test
          void test() { }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class enum source wrong type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        enum TestEnum { FIRST, SECOND, THIRD }      
        
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.EnumSource(<warning descr="No implicit conversion found to convert 'TestEnum' to 'int'">TestEnum.class</warning>)
        class TestWithEnumSource {
          @org.junit.jupiter.params.Parameter
          int i;
        
          @org.junit.jupiter.api.Test
          void test() { }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class multiple types highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.<warning descr="Exactly one type of input must be provided">ValueSource</warning>(
          ints = {1}, strings = "str"
        )
        class TestWithMultipleValues {
          @org.junit.jupiter.params.Parameter
          int i;
        
          @org.junit.jupiter.api.Test
          void test() { }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class no value defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.<warning descr="No value source is defined">ValueSource</warning>()
        class TestWithNoValues {
          @org.junit.jupiter.params.Parameter
          int i;

          @org.junit.jupiter.api.Test
          void test() { }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class no argument defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        <warning descr="'@NullSource' cannot provide an argument to constructor ['TestWithNullSrcNoParam']: no formal parameters declared">@org.junit.jupiter.params.provider.NullSource</warning>
        class TestWithNullSrcNoParam {
          @org.junit.jupiter.api.Test
          void test() {}
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class must declare a single constructor highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource("a")
        class TestMethodSource {
          public <warning descr="Class TestMethodSource must declare a single constructor">TestMethodSource</warning>(String argument) { }
          public <warning descr="Class TestMethodSource must declare a single constructor">TestMethodSource</warning>(String argument1, String argument2) { }

          @org.junit.jupiter.api.Test
          void test() { }
          
          static String[] a() { return new String[] {"a", "b"}; }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class method source should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' must be static">"a"</warning> })
        class TestMethodSource {
          @org.junit.jupiter.params.Parameter
          String param;

          @org.junit.jupiter.api.Test
          void test() { }
          
          String[] a() { return new String[] {"a", "b"}; }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class method source should not be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource("a")
        @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
        class TestMethodSource {
          @org.junit.jupiter.params.Parameter
          String param;

          @org.junit.jupiter.api.Test
          void test() { }
          
          String[] a() { return new String[] {"a", "b"}; }
        }
        """.trimIndent())
    }

    fun `test malformed parameterized class method source should have no parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        @org.junit.jupiter.params.ParameterizedClass
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' should have no parameters">"a"</warning> })
        class TestMethodSource {
          @org.junit.jupiter.params.Parameter
          String param;

          @org.junit.jupiter.api.Test
          void test() { }
          
          static String[] a(int i) { return new String[] {"a", "b"}; }
        }        
        """.trimIndent())
    }

    /* Malformed parameterized */
    fun `test malformed parameterized @ValueSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      enum TestEnum { FIRST, SECOND, THIRD }
      
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        void testWithIntValues(int i) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(longs = {1L})
        void testWithIntValues(long i) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(doubles = {0.5})
        void testWithDoubleValues(double d) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {""})
        void testWithStringValues(String s) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        void implicitParameter(String argument, org.junit.jupiter.api.TestInfo testReporter) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "FIRST" })
        void implicitConversionEnum(TestEnum e) { }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "1" })
        void implicitConversionString(int i) { }
          
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "title")
        void implicitConversionClass(Book book) { }

        static class Book { public Book(String title) { } }
        
        static class StaticInnerTest {
          @org.junit.jupiter.params.ParameterizedTest
          @org.junit.jupiter.params.provider.ValueSource(strings = {"1","2"})
          public void test(String data) {}
        }
        
        @org.junit.jupiter.api.Nested
        class NestedInnerTest {
          @org.junit.jupiter.params.ParameterizedTest
          @org.junit.jupiter.params.provider.ValueSource(strings = {"1","2"})
          public void test(String data) {}
        }
      }
      """.trimIndent()
      )
    }

    fun `test malformed parameterized @MethodSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("stream")
        void simpleStream(int x, int y) { System.out.println(x + ", " + y); }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("stream()")
        void withBraces(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("iterable")
        void simpleIterable(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("iterator")
        void simpleIterator(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = {"stream", "iterator", "iterable"})
        void parametersArray(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({"stream", "iterator"})
        void implicitValueArray(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "argumentsArrayProvider")
        void argumentsArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "objectsArrayProvider")
        void objectsArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "objects2DArrayProvider")
        void objects2DArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        void intStream(int x) { System.out.println(x); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        void injectTestReporter(int x, org.junit.jupiter.api.TestReporter testReporter) { System.out.println(x); }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        void intStreamProvider(int x, org.junit.jupiter.api.TestReporter testReporter) { System.out.println(x); }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("MethodSource${'$'}InnerClass#intStreamProvider()")
        void directLink(int x) { System.out.println(x); }

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stream() { return null; }
        static java.util.Iterator<org.junit.jupiter.params.provider.Arguments> iterator() { return null; }
        static Iterable<org.junit.jupiter.params.provider.Arguments> iterable() { return null; }
        static org.junit.jupiter.params.provider.Arguments[] argumentsArrayProvider() { 
          return new org.junit.jupiter.params.provider.Arguments[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; 
        }
        static Object[] objectsArrayProvider() { return new Object[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; }
        static Object[][] objects2DArrayProvider() { return new Object[][] { {1, "s"} }; }
        static java.util.stream.IntStream intStreamProvider() { return null; }
        
        static class InnerClass {
          static java.util.stream.IntStream intStreamProvider() { return null; }
        }
      }
      
      interface MyMethodSourceInterface {
        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> jdks() {
          return java.util.stream.Stream.of("11", "17").map(org.junit.jupiter.params.provider.Arguments::of);
        }
      }
      
      class MultiSourceTest implements MyMethodSourceInterface {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("jdks")
        public void test(String jdk) {}
        public static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> jdks() {
          return java.util.stream.Stream.of("1.8", "9").map(org.junit.jupiter.params.provider.Arguments::of);
        }
      }
      
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class TestWithMethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }
      
        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }
      """.trimIndent()
      )
    }

    fun `test malformed parameterized @TestInstance PER_CLASS no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @PerClass
      abstract class PerClassBase1 {
        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }
      
      class PerClassTest1 extends PerClassBase1 {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }
      }
      
      abstract class PerClassBase2 {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }
      }
      
      @PerClass
      class PerClassTest2 extends PerClassBase2 {
        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }
      
      abstract class PerClassBase3 {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }

        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }

      @PerClass
      class PerClassTest3 extends PerClassBase3 {
      }
      
      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      @interface PerClass { }
      """.trimIndent())
    }

    fun `test malformed parameterized @FieldSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class FieldSource {
         @org.junit.jupiter.params.ParameterizedTest
         @org.junit.jupiter.params.provider.FieldSource("collection")
         void simpleCollection(int x, int y) { System.out.println(x + ", " + y); }

         public static final java.util.Collection<Object> collection = null;

         @org.junit.jupiter.params.ParameterizedTest
         @org.junit.jupiter.params.provider.FieldSource("list")
         void simpleList(int x, int y) { System.out.println(x + ", " + y); }

         public static final java.util.List<Object> list = null;
         
         @org.junit.jupiter.params.ParameterizedTest
         @org.junit.jupiter.params.provider.FieldSource
         void stream(String str) { System.out.println(str); }

         public static final java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stream = null;
         
         @org.junit.jupiter.params.ParameterizedTest
         @org.junit.jupiter.params.provider.FieldSource
         void supplier(String str) { System.out.println(str); }

         public static final java.util.function.Supplier<java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>> supplier = null;
      }
      """.trimIndent())
    }

    fun `test malformed parameterized @EnumSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      enum TestEnum { FIRST, SECOND, THIRD }
      
      class EnumSource { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(names = "FIRST")
        void runTest(TestEnum value) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = TestEnum.class,
          names = "regexp-value",
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.MATCH_ALL
        )
        void disable() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(value = TestEnum.class, names = {"SECOND", "FIRST"/*, "commented"*/})
        void array() {  }        
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(TestEnum.class)
        void testWithEnumSourceCorrect(TestEnum value) { }        
      }
      """.trimIndent())
    }

    fun `test malformed parameterized @CsvSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class CsvSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(value = "src, 1")
        void testWithCsvSource(String first, int second) { }  
      }
      """.trimIndent()
      )
    }

    fun `test malformed parameterized @NullSource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class NullSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        void testWithNullSrc(Object o) { }      
      }
      """.trimIndent()
      )
    }

    fun `test @NullSource with parameter resolver`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.junit.jupiter.api.extension.ExtendWith;
        import org.junit.jupiter.api.extension.ParameterResolver;
        import org.junit.jupiter.params.ParameterizedTest;
        import org.junit.jupiter.params.provider.NullSource;
        import org.junit.jupiter.params.provider.ValueSource;
        
        @ExtendWith(ParameterResolver.class)
        class SimpleTest {
            @ParameterizedTest
            @NullSource
            void testWithNullSrc(Object o) { }
        }
      """.trimIndent())
    }

    fun `test malformed parameterized @EmptySource no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testString(String input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testList(java.util.Collection<String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testList(java.util.Map<String, String> input) { }        
      
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testList(java.util.List<String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testSet(java.util.Set<String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testSortedSet(java.util.SortedSet<String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testNavigableSet(java.util.NavigableSet<String> input) { }     
           
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testZeroArgSet(java.util.HashSet<String> input) { }                
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testMap(java.util.Map<String, String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testSortedMap(java.util.SortedMap<String, String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testNavigableMap(java.util.NavigableMap<String, String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testZeroArgMap(java.util.HashMap<String, String> input) { }        
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testWithTestInfo(String input, org.junit.jupiter.api.TestInfo testInfo) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testPrimitiveArray(int[] input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testObjectArray(Object[] input) { }     

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testMultiDimensionalObjectArray(Object[][] input) { }                    
      }      
    """.trimIndent())
    }

    fun `test malformed parameterized empty source map with zero-arg constructor`() {
      myFixture.addClass("""
      import java.util.HashMap;
      
      public class MyArgMap extends HashMap<String, String> {
        public MyArgMap() { }
      }
    """.trimIndent())
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testArgMap(MyArgMap input) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized empty source map with single arg constructor`() {
      myFixture.addClass("""
      import java.util.HashMap;
      
      public class MyArgMap extends HashMap<String, String> {
        public MyArgMap(String input) { }
      }
    """.trimIndent())
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgMap' type">@org.junit.jupiter.params.provider.EmptySource</warning>
        void testArgMap(MyArgMap input) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized empty source collection with private zero-arg constructor`() {
      myFixture.addClass("""
      import java.util.HashSet;
      
      public class MyArgSet extends HashSet<String, String> {
        private MyArgSet() { }
      }
    """.trimIndent())
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgSet' type">@org.junit.jupiter.params.provider.EmptySource</warning>
        void testArgSet(MyArgSet input) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized empty source collection with single arg constructor`() {
      myFixture.addClass("""
      import java.util.HashSet;
      
      public class MyArgSet extends HashSet<String, String> {
        public MyArgSet(String input) { }
      }
    """.trimIndent())
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgSet' type">@org.junit.jupiter.params.provider.EmptySource</warning>
        void testArgSet(MyArgSet input) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized value source wrong type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(booleans = {
          <warning descr="No implicit conversion found to convert 'boolean' to 'int'">false</warning>
        })
        void testWithBooleanSource(int argument) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized enum source wrong type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      enum TestEnum { FIRST, SECOND, THIRD }
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(<warning descr="No implicit conversion found to convert 'TestEnum' to 'int'">TestEnum.class</warning>)
        void testWithEnumSource(int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed ParameterizedTest inner class should be nested highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class OuterTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"1","2"})
        public void test(String data) {}
      
        class <warning descr="Tests in nested class will not be executed">InnerTest</warning> {
          @org.junit.jupiter.params.ParameterizedTest
          @org.junit.jupiter.params.provider.ValueSource(strings = {"1","2"})
          public void test(String data) {}
        }
      }
      """.trimIndent())
    }

    fun `test malformed parameterized multiple types highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="Exactly one type of input must be provided">ValueSource</warning>(
          ints = {1}, strings = "str"
        )
        void testWithMultipleValues(int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized no value defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="No value source is defined">ValueSource</warning>()
        void testWithNoValues(int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized no argument defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@NullSource' cannot provide an argument to method because method doesn't have parameters">@org.junit.jupiter.params.provider.NullSource</warning>
        void testWithNullSrcNoParam() {}
      }
    """.trimIndent())
    }

    fun `test malformed parameterized value source multiple parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        void <warning descr="Only a single parameter can be provided by '@ValueSource'">testWithMultipleParams</warning>(String argument, int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized and test annotation defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <warning descr="Suspicious combination of '@Test' and '@ParameterizedTest'">testWithTestAnnotation</warning>(int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized and value source defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <warning descr="Suspicious combination of '@ValueSource' and '@Test'">testWithTestAnnotationNoParameterized</warning>(int i) { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized no argument source provided highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ArgumentsSources({})
        void <warning descr="No sources are provided, the suite would be empty">emptyArgs</warning>(String param) { }
      }        
    """.trimIndent())
    }

    fun `test malformed parameterized method source should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' must be static">"a"</warning> })
        void foo(String param) { }
        
        String[] a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }

    fun `test malformed parameterized method source should have no parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' should have no parameters">"a"</warning> })
        void foo(String param) { }
        
        static String[] a(int i) { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }

    fun `test malformed parameterized method source wrong return type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">"a"</warning> })
        void foo(String param) { }
        
        static Object a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }

    fun `test malformed parameterized method source not found highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Cannot resolve target method source: 'a'">"a"</warning> })
        void foo(String param) { }
      }        
    """.trimIndent())
    }

    fun `test malformed field source should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.FieldSource({ <warning descr="Field source 'a' must be static">"a"</warning> })
        void foo(String param) { }
        
        java.util.List<String> a = null;
      }        
    """.trimIndent())
    }

    fun `test malformed field source wrong return type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.FieldSource({ <warning descr="Field source 'a' type must be convertible to a Stream">"a"</warning> })
        void foo(String param) { }
        
        static final Integer a = 1;
      }        
    """.trimIndent())
    }

    fun `test malformed field source not found highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.FieldSource({ <warning descr="Cannot resolve target field source: 'a'">"a"</warning> })
        void foo(String param) { }
      }        
    """.trimIndent())
    }

    fun `test malformed parameterized enum source unresolvable entry highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class EnumSourceTest {
        private enum Foo { AAA, AAX, BBB }
      
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>, 
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
        )
        void invalid() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>
       )
        void invalidDefault() { }
      }
    """.trimIndent())
    }

    fun `test malformed parameterized add test instance quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      class Test {
        private Stream<Arguments> parameters() { return null; }
      
        @MethodSource("param<caret>eters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.TestInstance;
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        private Stream<Arguments> parameters() { return null; }
      
        @MethodSource("parameters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), "Annotate class 'Test' as '@TestInstance'", testPreview = true)
    }

    fun `test malformed parameterized introduce method source quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      
      class Test {
        @MethodSource("para<caret>meters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      class Test {
          public static Stream<Arguments> parameters() {
              return null;
          }
      
          @MethodSource("parameters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), "Create method 'parameters' in 'Test'", testPreview = true)
    }

    fun `test malformed field source should be static quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      
      class Test {
        private List<String> list = null;
      
        @FieldSource("li<caret>st")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.TestInstance;
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.FieldSource;
      import java.util.List;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        private List<String> list = null;
      
        @FieldSource("list")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), "Annotate class 'Test' as '@TestInstance'", testPreview = true)
    }

    fun `test malformed introduce field source quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      
      class Test {
          @FieldSource("li<caret>st")
          @ParameterizedTest
          void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.FieldSource;

      import java.util.Collection;

      class Test {
          private static final Collection<Arguments> list = ;

          @FieldSource("list")
          @ParameterizedTest
          void foo(String param) { }
      }
    """.trimIndent(), "Create constant field 'list' in 'Test'", testPreview = true)
    }

    fun `test malformed parameterized create csv source quickfix`() {
      val file = myFixture.addFileToProject("CsvFile.java", """
        class CsvFile {
            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.CsvFileSource(resources = "two-<caret>column.txt")
            void testWithCsvFileSource(String first, int second) { }
        }
    """.trimIndent())
      myFixture.configureFromExistingVirtualFile(file.virtualFile)
      val intention = myFixture.findSingleIntention("Create file two-column.txt")
      assertNotNull(intention)
      myFixture.launchAction(intention)
      assertNotNull(myFixture.findFileInTempDir("two-column.txt"))
    }

    /* Malformed repeated test*/
    fun `test malformed repeated test no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestNoParams() { }

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithRepetitionInfo(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }

        @org.junit.jupiter.api.BeforeEach
        void config(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }

      class WithRepeatedAndCustomNames {
        @org.junit.jupiter.api.RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
        void repeatedTestWithCustomName() { }
      }

      class WithRepeatedAndTestInfo {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.TestInfo testInfo, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithTestInfo(org.junit.jupiter.api.TestInfo testInfo) { }

        @org.junit.jupiter.api.AfterEach
        void afterEach(org.junit.jupiter.api.TestInfo testInfo, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}
      }

      class WithRepeatedAndTestReporter {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.TestReporter testReporter, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithTestInfo(org.junit.jupiter.api.TestReporter testReporter) { }

        @org.junit.jupiter.api.AfterEach
        void afterEach(org.junit.jupiter.api.TestReporter testReporter, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}
      }
    """.trimIndent())
    }

    fun `test malformed repeated test combination of @Test and @RepeatedTest highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeatedAndTests {
        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.RepeatedTest(1)
        void <warning descr="Suspicious combination of '@Test' and '@RepeatedTest'">repeatedTestAndTest</warning>() { }
      }    
    """.trimIndent())
    }

    fun `test malformed repeated test with injected RepeatedInfo for @Test method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeatedInfoAndTest {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }

        @org.junit.jupiter.api.Test
        void <warning descr="Method 'nonRepeated' annotated with '@Test' should not declare parameter 'repetitionInfo'">nonRepeated</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
        
        @org.junit.jupiter.api.Test
        void <warning descr="Method 'nonRepeated' annotated with '@Test' should not declare parameters 'repetitionInfo' and 'a'">nonRepeated</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo, int a) { }
      }      
    """.trimIndent())
    }

    fun `test malformed repeated test with injected RepetitionInfo for @BeforeAll method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithBeforeEach {
        @org.junit.jupiter.api.BeforeAll
        void <warning descr="Method 'beforeAllWithRepetitionInfo' annotated with '@BeforeAll' should be static and not declare parameter 'repetitionInfo'">beforeAllWithRepetitionInfo</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
        
        @org.junit.jupiter.api.AfterAll
        void <warning descr="Method 'afterAllWithRepetitionInfo' annotated with '@AfterAll' should be static and not declare parameters 'repetitionInfo' and 'a'">afterAllWithRepetitionInfo</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo, String a) { }
      }
    """.trimIndent())
    }

    fun `test malformed repeated test with non-positive repetitions highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
        void repeatedTestNegative() { }

        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
        void repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
    }

    /* Malformed before after */
    fun `test malformed before highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.Before
        String <warning descr="Method 'before' annotated with '@Before' should be public, of type 'void' and not declare parameter 'i'">before</warning>(int i) { return ""; }
        
        @org.junit.After
        String <warning descr="Method 'after' annotated with '@After' should be public, of type 'void' and not declare parameters 'a', 'b', 'c' and 'd'">after</warning>(int a, boolean b, String c, char d) { return ""; }
      }
    """.trimIndent())
    }

    fun `test malformed before each highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        String <warning descr="Method 'beforeEach' annotated with '@BeforeEach' should be of type 'void' and not declare parameter 'i'">beforeEach</warning>(int i) { return ""; }
        
        @org.junit.jupiter.api.AfterEach
        String <warning descr="Method 'afterEach' annotated with '@AfterEach' should be of type 'void' and not declare parameters 'i' and 'j'">afterEach</warning>(int i, int j) { return ""; }
      }
    """.trimIndent())
    }

    fun `test malformed before change signature quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.Before
        String bef<caret>ore(int i) { return ""; }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.Before
        public void before() { return ""; }
      }
    """.trimIndent(), "Fix 'before' method signature", testPreview = true)
    }

    fun `test malformed before remove private quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        private void bef<caret>oreEach() { }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        public void beforeEach() { }
      }
    """.trimIndent(), "Fix 'beforeEach' method signature", testPreview = true)
    }

    fun `test malformed before class no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class BeforeAllStatic {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }  
            
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class BeforeAllTestInstancePerClass {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }
      

      class TestParameterResolver implements org.junit.jupiter.api.extension.ParameterResolver {
        @Override
        public boolean supportsParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) { return false; }

        @Override
        public Object resolveParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) { return null; }
      }

      @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver.class)
      class ParameterResolver {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll(String foo) { }
      }
    """.trimIndent())
    }

    fun `test non-malformed with multiple extensions inside extensions annotation`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        class TestNonParameterResolver implements org.junit.jupiter.api.extension.Extension { }

        class TestParameterResolver implements org.junit.jupiter.api.extension.ParameterResolver {
            @Override
            public boolean supportsParameter(
              org.junit.jupiter.api.extension.ParameterContext parameterContext,
              org.junit.jupiter.api.extension.ExtensionContext extensionContext
            ) { return false; }

            @Override
            public Object resolveParameter(
              org.junit.jupiter.api.extension.ParameterContext parameterContext,
              org.junit.jupiter.api.extension.ExtensionContext extensionContext
            ) { return null; }
        }

        @org.junit.jupiter.api.extension.Extensions({
            @org.junit.jupiter.api.extension.ExtendWith(TestNonParameterResolver.class),
            @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver.class)
        })
        class ParameterResolver {
            @org.junit.jupiter.api.BeforeAll
            public static void beforeAll(String foo) { }
        }

      """.trimIndent())
    }

    fun `test malformed before class highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should be static, of type 'void' and not declare parameter 'i'">beforeAll</warning>(int i) { return ""; }
        
        @org.junit.jupiter.api.AfterAll
        String <warning descr="Method 'afterAll' annotated with '@AfterAll' should be static, of type 'void' and not declare parameters 'i', 'j' and 'k'">afterAll</warning>(int i, int j, int k) { return ""; }
      }
    """.trimIndent())
    }

    fun `test malformed before all quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String before<caret>All(int i) { return ""; }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        static void beforeAll() { return ""; }
      }
    """.trimIndent(), "Fix 'beforeAll' method signature", testPreview = true)
    }

    fun `test no highlighting when automatic parameter resolver is found`() {
      myFixture.addFileToProject("com/intellij/testframework/ext/AutomaticExtension.java", """
      package com.intellij.testframework.ext;
      
      class AutomaticExtension implements org.junit.jupiter.api.extension.ParameterResolver {
        @Override
        public boolean supportsParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) {
          return true;
        }
    
        @Override
        public Object resolveParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) {
          return "";
        }
      }    
    """.trimIndent())
      addAutomaticExtension("com.intellij.testframework.ext.AutomaticExtension")
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        public void foo(int x) { }
      }
    """.trimIndent())
    }

    fun `test no highlighting on inherited parameter resolver`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.junit.jupiter.api.extension.*;
        import org.junit.jupiter.api.Test;
        
        class MyParameterResolver implements ParameterResolver {
            @Override
            public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                return true;
            }

            @Override
            public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                return new Integer(0);
            }
        }        
        
        @ExtendWith(MyParameterResolver.class)
        abstract class BaseTest { }
        
        class SubBaseTest extends BaseTest { }
        
        public class SomeTest extends SubBaseTest {
            @Test
            void someTest(Integer parameter) { }
        }
      """.trimIndent(), fileName = "SomeTest")
    }

    /* Malformed Datapoint(s) */
    fun `test malformed dataPoint no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public static Object f1;
      }
    """.trimIndent())
    }

    fun `test malformed dataPoint non-static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public Object <warning descr="Field 'f1' annotated with '@DataPoint' should be static">f1</warning>;
      }
    """.trimIndent())
    }

    fun `test malformed dataPoint non-public highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint static Object <warning descr="Field 'f1' annotated with '@DataPoint' should be public">f1</warning>;
      }
    """.trimIndent())
    }

    fun `test malformed dataPoint field highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Field 'f1' annotated with '@DataPoint' should be static and public">f1</warning>;
      }
    """.trimIndent())
    }

    fun `test malformed datapoint method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Method 'f1' annotated with '@DataPoint' should be static and public">f1</warning>() { return null; }
      }
    """.trimIndent())
    }

    fun `test malformed datapoints method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoints Object <warning descr="Method 'f1' annotated with '@DataPoints' should be static and public">f1</warning>() { return null; }
      }
    """.trimIndent())
    }

    fun `test malformed dataPoint quickfix make method public and static`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object f<caret>1() { return null; }
      }
    """.trimIndent(), """
      class Test {
        @org.junit.experimental.theories.DataPoint
        public static Object f1() { return null; }
      }
    """.trimIndent(), "Fix 'f1' method signature", testPreview = true)
    }

    /* Malformed setup/teardown */
    fun `test malformed setup no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class C extends junit.framework.TestCase {
        @Override
        public void setUp() { }
      }  
    """.trimIndent(), "C")
    }

    fun `test malformed setup highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class C extends junit.framework.TestCase {
        private void <warning descr="Method 'setUp' should be non-private, non-static, have no parameters and of type void">setUp</warning>(int i) { }
      }  
    """.trimIndent(), "C")
    }

    fun `test malformed setup quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class C extends junit.framework.TestCase {
        private void set<caret>Up(int i) { }
      }  
    """.trimIndent(), """
      class C extends junit.framework.TestCase {
        public void setUp() { }
      }  
    """.trimIndent(), "Fix 'setUp' method signature", testPreview = true)
    }

    /* Malformed rule */
    fun `test malformed rule field non-public highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class RuleTest {
        @org.junit.Rule
        private SomeTestRule <warning descr="Field 'x' annotated with '@Rule' should be public">x</warning>;

        @org.junit.Rule
        public static SomeTestRule <warning descr="Field 'y' annotated with '@Rule' should be non-static">y</warning>;
      }
    """.trimIndent())
    }

    fun `test malformed rule field non TestRule type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class RuleTest {
        @org.junit.Rule
        public int <warning descr="Field 'x' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">x</warning>;
      }
    """.trimIndent())
    }

    fun `test malformed rule method static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class RuleTest {        
        @org.junit.Rule
        public static SomeTestRule <warning descr="Method 'y' annotated with '@Rule' should be non-static">y</warning>() { 
          return new SomeTestRule();  
        };        
      }
    """.trimIndent())
    }

    fun `test malformed class rule field highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        static SomeTestRule <warning descr="Field 'x' annotated with '@ClassRule' should be public">x</warning> = new SomeTestRule();

        @org.junit.ClassRule
        public SomeTestRule <warning descr="Field 'y' annotated with '@ClassRule' should be static">y</warning> = new SomeTestRule();

        @org.junit.ClassRule
        private SomeTestRule <warning descr="Field 'z' annotated with '@ClassRule' should be static and public">z</warning> = new SomeTestRule();

        @org.junit.ClassRule
        public static int <warning descr="Field 't' annotated with '@ClassRule' should be of type 'org.junit.rules.TestRule'">t</warning> = 0;
      }
    """.trimIndent())
    }

    fun `test malformed rule make field public quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class RuleQfTest {
        @org.junit.Rule
        private int x<caret>;
      }
    """.trimIndent(), """      
      class RuleQfTest {
        @org.junit.Rule
        public int x;
      }
    """.trimIndent(), "Fix 'x' field signature", testPreview = true)
    }

    fun `test malformed rule make field non-static quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class RuleQfTest {
        @org.junit.Rule
        public static int y<caret>() { return 0; }
      }
    """.trimIndent(), """
      class RuleQfTest {
        @org.junit.Rule
        public int y() { return 0; }
      }
    """.trimIndent(), "Fix 'y' method signature", testPreview = true)
    }

    fun `test malformed class rule make field public quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        static SomeTestRule x<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule x = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'x' field signature", testPreview = true)
    }

    fun `test malformed class rule make field static quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public SomeTestRule y<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule y = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'y' field signature")
    }

    fun `test malformed class rule make field public and static quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        private SomeTestRule z<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule z = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'z' field signature")
    }

    /* Malformed test */
    fun `test malformed test for JUnit 3 highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit3TestMethodIsPublicVoidNoArg extends junit.framework.TestCase {
        void <warning descr="Method 'testOne' should be public, non-static, have no parameters and of type void">testOne</warning>() { }
        public int <warning descr="Method 'testTwo' should be public, non-static, have no parameters and of type void">testTwo</warning>() { return 2; }
        public static void <warning descr="Method 'testThree' should be public, non-static, have no parameters and of type void">testThree</warning>() { }
        public void <warning descr="Method 'testFour' should be public, non-static, have no parameters and of type void">testFour</warning>(int i) { }
        public void testFive() { }
        void testSix(int i) { } //ignore when method doesn't look like test anymore
      }
    """.trimIndent(), "JUnit3TestMethodIsPublicVoidNoArg")
    }

    fun `test malformed test for JUnit 4 highlighting`() {
      myFixture.addClass("""
      package mockit;
      public @interface Mocked { }
    """.trimIndent())
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class JUnit4TestMethodIsPublicVoidNoArg {
        @org.junit.Test void <warning descr="Method 'testOne' annotated with '@Test' should be public">testOne</warning>() {}
        @org.junit.Test public int <warning descr="Method 'testTwo' annotated with '@Test' should be of type 'void'">testTwo</warning>() { return 2; }
        @org.junit.Test public static void <warning descr="Method 'testThree' annotated with '@Test' should be non-static">testThree</warning>() {}
        @org.junit.Test public void <warning descr="Method 'testFour' annotated with '@Test' should not declare parameter 'i'">testFour</warning>(int i) {}
        @org.junit.Test public void testFive() {}
        @org.junit.Test public void <warning descr="Method 'testSix' annotated with '@Test' should not declare parameters 'i', 'j' and 'k'">testSix</warning>(int i, int j, int k) {}
        @org.junit.Test public void testMock(@mockit.Mocked String s) {}
      }
    """.trimIndent(), "JUnit4TestMethodIsPublicVoidNoArg")
    }

    fun `test no highlighting on custom runner`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyRunner extends org.junit.runner.Runner {
          @Override
          public org.junit.runner.Description getDescription() { return null; }
        
          @Override
          public void run(org.junit.runner.notification.RunNotifier notifier) { }
      }
      
      @org.junit.runner.RunWith(MyRunner.class)
      class Foo {
          @org.junit.Test 
          public int testMe(int i) { return -1; }
      }
    """.trimIndent())
    }

    fun `test highlighting on predefined runner`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
      class Foo {
          @org.junit.Test 
          public int <warning descr="Method 'testMe' annotated with '@Test' should be of type 'void' and not declare parameter 'i'">testMe</warning>(int i) { return -1; }
          
          @org.junit.Test 
          public int <warning descr="Method 'testMe' annotated with '@Test' should be of type 'void' and not declare parameters 'a', 'b' and 'c'">testMe</warning>(int a, String b, boolean c) { return -1; }
      }
    """.trimIndent())
    }

    fun `test no highlighting malformed test with parameter resolver`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.extension.*;
      import org.junit.jupiter.api.Test;
      
      class MyResolver implements ParameterResolver {
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
          return true;
        }
           
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException { 
          return null;
        }
      }
      
      @ExtendWith(MyResolver.class)
      class Foo {
        @Test
        void parametersExample(String a, String b) { }
      }
      
      @ExtendWith(MyResolver.class)
      @interface ResolverAnnotation { }
      
      @ExtendWith(MyResolver.class)
      class Bar {
        @org.junit.jupiter.api.extension.RegisterExtension
        static final MyResolver integerResolver = new MyResolver();
      
        @Test
        void parametersExample(String a, String b) { }
      }
      
      class FooBar {
        @Test
        void parametersExample(@ResolverAnnotation String a, @ResolverAnnotation String b) { }
      }
    """.trimIndent())
    }

    fun `test no highlighting malformed test with nested parameter resolver`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.extension.*;
      import org.junit.jupiter.api.Nested;
      import org.junit.jupiter.api.Test;
      
      class MyResolver implements ParameterResolver {
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
          return true;
        }
           
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException { 
          return null;
        }
      }
      
      @ExtendWith(MyResolver.class)
      class Foo {
        @Nested
        class Bar {
          @Test
          void parametersExample(String a, String b) { }
        }
      }
    """.trimIndent())
    }

    fun `test no highlighting for programmatically registered parameter resolver`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.junit.jupiter.api.extension.*;
        import org.junit.jupiter.api.Test;
        
        class MyResolver implements ParameterResolver {
          @Override
          public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return true;
          }
             
          @Override
          public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException { 
            return null;
          }
        }       
        
        class Bar {
          @org.junit.jupiter.api.extension.RegisterExtension
          static final MyResolver integerResolver = new MyResolver();
        
          @Test
          void parametersExample(String a, String b) { }
        } 
      """.trimIndent())
    }

    fun `test malformed before and after suite should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.platform.suite.api.Suite
      @org.junit.platform.suite.api.SelectClasses({MyTest.class})
      class MySuite {
        @org.junit.platform.suite.api.BeforeSuite
        public void <warning descr="Method 'before' annotated with '@BeforeSuite' should be static">before</warning>() { }
        
        @org.junit.platform.suite.api.AfterSuite
        public void <warning descr="Method 'after' annotated with '@AfterSuite' should be static">after</warning>() { }
      }
      
      class MyTest {}
    """.trimIndent())
    }

    fun `test malformed before and after suite should be of type void highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.platform.suite.api.Suite
      @org.junit.platform.suite.api.SelectClasses({MyTest.class})
      class MySuite {
        @org.junit.platform.suite.api.BeforeSuite
        public static int <warning descr="Method 'before' annotated with '@BeforeSuite' should be of type 'void'">before</warning>() { return 0; }
        
        @org.junit.platform.suite.api.AfterSuite
        public static int <warning descr="Method 'after' annotated with '@AfterSuite' should be of type 'void'">after</warning>() { return 0; }
      }
      
      class MyTest {}
    """.trimIndent())
    }

    fun `test malformed before and after suite should be not private highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.platform.suite.api.Suite
      @org.junit.platform.suite.api.SelectClasses({MyTest.class})
      class MySuite {
        @org.junit.platform.suite.api.BeforeSuite
        private static void <warning descr="Method 'before' annotated with '@BeforeSuite' should be public">before</warning>() { }
        
        @org.junit.platform.suite.api.AfterSuite
        public static void after1() { }
        
        @org.junit.platform.suite.api.AfterSuite
        static void after2() { }
      }
      
      class MyTest {}
    """.trimIndent())
    }

    fun `test malformed before and after suite should not declare parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.platform.suite.api.Suite
      @org.junit.platform.suite.api.SelectClasses({MyTest.class})
      class MySuite {
        @org.junit.platform.suite.api.BeforeSuite
        public static void <warning descr="Method 'before' annotated with '@BeforeSuite' should not declare parameter 'a'">before</warning>(int a) { }
        
        @org.junit.platform.suite.api.AfterSuite
        public static void <warning descr="Method 'after' annotated with '@AfterSuite' should not declare parameters 'test', 'suite' and 'a'">after</warning>(MyTest test, MySuite suite, int a) { }
      }
      
      class MyTest {}
    """.trimIndent())
    }

    // Unconstructable test case
    fun testPlain() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Plain { }
    """.trimIndent())
    }

    fun testUnconstructableJUnit3TestCase1() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class <warning descr="Test class 'UnconstructableJUnit3TestCase1' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase1</warning> extends TestCase {
          private UnconstructableJUnit3TestCase1() {
              System.out.println("");
          }
      }

    """.trimIndent())
    }

    fun testUnconstructableJUnit3TestCase2() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class <warning descr="Test class 'UnconstructableJUnit3TestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase2</warning> extends TestCase {
          public UnconstructableJUnit3TestCase2(Object foo) {
              System.out.println("");
          }
      }

    """.trimIndent())
    }

    fun testUnconstructableJUnit3TestCase3() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCase3 extends TestCase {
          public UnconstructableJUnit3TestCase3() {
              System.out.println("");
          }
      }

    """.trimIndent())
    }

    fun testUnconstructableJUnit3TestCase4() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCase4 extends TestCase {
          public UnconstructableJUnit3TestCase4(String foo) {
              System.out.println("");
          }
      }
    """.trimIndent())
    }

    fun testUnconstructableJUnit3TestCaseLocalClass() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCaseLocalClass {
          public static void main() {
            class LocalClass extends TestCase { }
          }
      }
    """.trimIndent())
    }

    fun testUnconstructableJUnit4TestCase1() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Test;
      
      public class <warning descr="Test class 'UnconstructableJUnit4TestCase1' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase1</warning> {
        public UnconstructableJUnit4TestCase1(String s) {}
        
        public UnconstructableJUnit4TestCase1() {}
      
        @Test
        public void testMe() {}
      }
    """.trimIndent())
    }

    fun testUnconstructableJUnit4TestCase2() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Test;

      public class UnconstructableJUnit4TestCase2 {
      	public UnconstructableJUnit4TestCase2() {
      		this("two", 1);
      	}

      	private UnconstructableJUnit4TestCase2(String one, int two) {
      		// do nothing with the parameters
      	}

      	@Test
      	public void testAssertion() {
      	}
      }
    """.trimIndent())
    }

    fun testUnconstructableJUnit4TestCase3() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.Test;

      class <warning descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it is not 'public'"><warning descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase3</warning></warning> {
        UnconstructableJUnit4TestCase3() {}

        @Test
        public void testMe() {}
      }
    """.trimIndent())
    }

    fun testConstructableJunit3WithJunit4runner() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.Collection;
      import java.util.Arrays;
      import junit.framework.TestCase;
      import org.junit.runner.RunWith;
      import org.junit.runners.Parameterized;
      import org.junit.Test;

      @RunWith(Parameterized.class)
      class ConstructableJunit3WithJunit4runner extends TestCase {
        ConstructableJunit3WithJunit4runner(Integer i) {}
        
        @Parameterized.Parameters
        public static Collection<Integer> params() {
          return Arrays.asList(1, 2, 3);
        }

        @Test
        public void testMe() {}
      }
    """.trimIndent())
    }

    fun `test malformed before and after suite requires Suite annotation highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.platform.suite.api.*;

      class MySuite {
        @BeforeSuite
        static void <warning descr="Method 'before' annotated with '@BeforeSuite' requires the containing class to be annotated with '@Suite'">before</warning>() {}
      
        @AfterSuite
        static void <warning descr="Method 'after' annotated with '@AfterSuite' requires the containing class to be annotated with '@Suite'">after</warning>() {}
      }
    """.trimIndent())
    }

    fun `test malformed before suite requires Suite annotation quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.platform.suite.api.*;

      class MySuite {
        @BeforeSuite
        static void befo<caret>re() {}
      }
    """.trimIndent(), """
      import org.junit.platform.suite.api.*;

      @Suite
      class MySuite {
        @BeforeSuite
        static void before() {}
      }
    """.trimIndent(), "Annotate class 'MySuite' as '@Suite'", testPreview = true)
    }

    fun `test suite without selectors highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.platform.suite.api.Suite;

      @Suite
      class <warning descr="Suite does not select any tests (no selector annotations)">MySuite1</warning> {
      }
      
      @Suite(failIfNoTests = true)
      class <warning descr="Suite does not select any tests (no selector annotations)">MySuite2</warning> {
      }
      
      @Suite(failIfNoTests = false)
      class MySuite3 {
      }
    """.trimIndent())
    }

    fun `test suite with selector no highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.platform.suite.api.*;

      @Suite
      @SelectClasses({})
      class MySuite1 {
      }
      
      @Suite
      @SelectPackages("com.example")
      class MySuite2 {
      }
      
      @Suite
      abstract class MyAbstractSuite { }
      
      @Suite
      interface MyInterfaceSuite { }
      
      @SelectPackages("com.example")
      interface MySelector {
      }
      
      @Suite
      class MySuite3 implements MySelector {
      }
      
      @SelectPackages("com.example")
      @interface CustomSelector { }
      
      @Suite
      @CustomSelector
      class MySuite4 {
      }
    """.trimIndent())
    }

    fun `test argument convertor highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class StringToBytesConverter implements org.junit.jupiter.params.converter.ArgumentConverter {
          @Override
          public Object convert(Object source, org.junit.jupiter.api.extension.ParameterContext context) {
              return ((String) source).getBytes();
          }
      }
      
      @java.lang.annotation.Target({java.lang.annotation.ElementType.ANNOTATION_TYPE, java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD})
      @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
      @org.junit.jupiter.params.converter.ConvertWith(StringToBytesConverter.class)
      @interface MyConverter {
      }

      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "")
        void direct(@org.junit.jupiter.params.converter.ConvertWith(StringToBytesConverter.class) byte[] bytes) {
        }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "")
        void inherited(@MyConverter byte[] bytes) {
        }
      }
      """.trimIndent())
    }

    fun `test suite without selectors quickfix`() {
      myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.platform.suite.api.Suite;

      @Suite
      class My<caret>Suite {
      }
    """.trimIndent(), """
      import org.junit.platform.suite.api.Suite;

      @Suite(failIfNoTests = false)
      class MySuite {
      }
    """.trimIndent(), "Set 'failIfNoTests' to 'false'", testPreview = true)
    }
  }
}