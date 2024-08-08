// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection

import com.intellij.junit.testFramework.JUnitMalformedDeclarationInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
class JavaJUnitMalformedDeclarationInspectionTest {
  class V57 : JUnitMalformedDeclarationInspectionTestBase(JUNIT5_7_0) {
    fun `test malformed private highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        private Rule5 <error descr="Field 'myRule5' annotated with '@RegisterExtension' should be public">myRule5</error> = new Rule5();
        class Rule5 implements org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
    }

    fun `test malformed empty source highlighting with hashSet`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
        class MyTest {
          @org.junit.jupiter.params.ParameterizedTest
          <error descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'HashSet<String>' type">@org.junit.jupiter.params.provider.EmptySource</error>
          void testZeroArgSet(java.util.HashSet<String> input) { }     
        }
      """.trimIndent())
    }
  }

  class Latest : JUnitMalformedDeclarationInspectionTestBase(JUNIT5_LATEST) {
    /* Malformed extensions */
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
        Rule5 <error descr="Field 'myRule5' annotated with '@RegisterExtension' should be of type 'org.junit.jupiter.api.extension.Extension'">myRule5</error> = new Rule5();
        class Rule5 { }
      }
    """.trimIndent())
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
    fun `test malformed nested class highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class <error descr="Tests in nested class will not be executed">B</error> { }
        
        @org.junit.jupiter.api.Nested
        private class <error descr="Tests in nested class will not be executed">C</error> { }
        
        @org.junit.jupiter.api.Nested
        private static class <error descr="Tests in nested class will not be executed">D</error> { }
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
        public class <error descr="Tests in nested class will not be executed">B</error> { 
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
        class <error descr="Tests in nested class will not be executed">B</error> { 
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

    /* Malformed parameterized */
    fun `test malformed parameterized no highlighting`() {
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
      }
      
      class MethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("stream")
        void simpleStream(int x, int y) { System.out.println(x + ", " + y); }

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

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stream() { return null; }
        static java.util.Iterator<org.junit.jupiter.params.provider.Arguments> iterator() { return null; }
        static Iterable<org.junit.jupiter.params.provider.Arguments> iterable() { return null; }
        static org.junit.jupiter.params.provider.Arguments[] argumentsArrayProvider() { 
          return new org.junit.jupiter.params.provider.Arguments[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; 
        }
        static Object[] objectsArrayProvider() { return new Object[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; }
        static Object[][] objects2DArrayProvider() { return new Object[][] { {1, "s"} }; }
        static java.util.stream.IntStream intStreamProvider() { return null; }
      }
      
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class TestWithMethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }
      
        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }
      
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
      
      class CsvSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(value = "src, 1")
        void testWithCsvSource(String first, int second) { }  
      }
      
      class NullSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        void testWithNullSrc(Object o) { }      
      }
    """.trimIndent()
      )
    }
    fun `test malformed parameterized empty source no highlighting`() {
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
        <error descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgMap' type">@org.junit.jupiter.params.provider.EmptySource</error>
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
        <error descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgSet' type">@org.junit.jupiter.params.provider.EmptySource</error>
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
        <error descr="'@EmptySource' cannot provide an argument to method because method has an unsupported parameter of 'MyArgSet' type">@org.junit.jupiter.params.provider.EmptySource</error>
        void testArgSet(MyArgSet input) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized value source wrong type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(booleans = {
          <error descr="No implicit conversion found to convert 'boolean' to 'int'">false</error>
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
        @org.junit.jupiter.params.provider.EnumSource(<error descr="No implicit conversion found to convert 'TestEnum' to 'int'">TestEnum.class</error>)
        void testWithEnumSource(int i) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized multiple types highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<error descr="Exactly one type of input must be provided">ValueSource</error>(
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
        @org.junit.jupiter.params.provider.<error descr="No value source is defined">ValueSource</error>()
        void testWithNoValues(int i) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized no argument defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        <error descr="'@NullSource' cannot provide an argument to method because method doesn't have parameters">@org.junit.jupiter.params.provider.NullSource</error>
        void testWithNullSrcNoParam() {}
      }
    """.trimIndent())
    }
    fun `test malformed parameterized value source multiple parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        void <error descr="Only a single parameter can be provided by '@ValueSource'">testWithMultipleParams</error>(String argument, int i) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized and test annotation defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <error descr="Suspicious combination of '@Test' and '@ParameterizedTest'">testWithTestAnnotation</error>(int i) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized and value source defined highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <error descr="Suspicious combination of '@ValueSource' and '@Test'">testWithTestAnnotationNoParameterized</error>(int i) { }
      }
    """.trimIndent())
    }
    fun `test malformed parameterized no argument source provided highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ArgumentsSources({})
        void <error descr="No sources are provided, the suite would be empty">emptyArgs</error>(String param) { }
      }        
    """.trimIndent())
    }
    fun `test malformed parameterized method source should be static highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <error descr="Method source 'a' must be static">"a"</error> })
        void foo(String param) { }
        
        String[] a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }
    fun `test malformed parameterized method source should have no parameters highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <error descr="Method source 'a' should have no parameters">"a"</error> })
        void foo(String param) { }
        
        static String[] a(int i) { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }
    fun `test malformed parameterized method source wrong return type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <error descr="Method source 'a' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">"a"</error> })
        void foo(String param) { }
        
        static Object a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
    }
    fun `test malformed parameterized method source not found highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <error descr="Cannot resolve target method source: 'a'">"a"</error> })
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
          names = <error descr="Can't resolve 'enum' constant reference.">"invalid-value"</error>, 
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
        )
        void invalid() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = <error descr="Can't resolve 'enum' constant reference.">"invalid-value"</error>
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
        void <error descr="Suspicious combination of '@Test' and '@RepeatedTest'">repeatedTestAndTest</error>() { }
      }    
    """.trimIndent())
    }
    fun `test malformed repeated test with injected RepeatedInfo for @Test method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeatedInfoAndTest {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }

        @org.junit.jupiter.api.Test
        void <error descr="Method 'nonRepeated' annotated with '@Test' should not declare parameter 'repetitionInfo'">nonRepeated</error>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }      
    """.trimIndent())
    }
    fun `test malformed repeated test with injected RepetitionInfo for @BeforeAll method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithBeforeEach {
        @org.junit.jupiter.api.BeforeAll
        void <error descr="Method 'beforeAllWithRepetitionInfo' annotated with '@BeforeAll' should be static and not declare parameter 'repetitionInfo'">beforeAllWithRepetitionInfo</error>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }
    """.trimIndent())
    }
    fun `test malformed repeated test with non-positive repetitions highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(<error descr="The number of repetitions must be greater than zero">-1</error>)
        void repeatedTestNegative() { }

        @org.junit.jupiter.api.RepeatedTest(<error descr="The number of repetitions must be greater than zero">0</error>)
        void repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
    }

    /* Malformed before after */
    fun `test malformed before highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.Before
        String <error descr="Method 'before' annotated with '@Before' should be public, of type 'void' and not declare parameter 'i'">before</error>(int i) { return ""; }
      }
    """.trimIndent())
    }
    fun `test malformed before each highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        String <error descr="Method 'beforeEach' annotated with '@BeforeEach' should be of type 'void' and not declare parameter 'i'">beforeEach</error>(int i) { return ""; }
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
    fun `test malformed before class highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String <error descr="Method 'beforeAll' annotated with '@BeforeAll' should be static, of type 'void' and not declare parameter 'i'">beforeAll</error>(int i) { return ""; }
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
        @org.junit.experimental.theories.DataPoint public Object <error descr="Field 'f1' annotated with '@DataPoint' should be static">f1</error>;
      }
    """.trimIndent())
    }
    fun `test malformed dataPoint non-public highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint static Object <error descr="Field 'f1' annotated with '@DataPoint' should be public">f1</error>;
      }
    """.trimIndent())
    }
    fun `test malformed dataPoint field highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <error descr="Field 'f1' annotated with '@DataPoint' should be static and public">f1</error>;
      }
    """.trimIndent())
    }
    fun `test malformed datapoint method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <error descr="Method 'f1' annotated with '@DataPoint' should be static and public">f1</error>() { return null; }
      }
    """.trimIndent())
    }
    fun `test malformed datapoints method highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoints Object <error descr="Method 'f1' annotated with '@DataPoints' should be static and public">f1</error>() { return null; }
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
        private void <error descr="Method 'setUp' should be non-private, non-static, have no parameters and of type void">setUp</error>(int i) { }
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
        private SomeTestRule <error descr="Field 'x' annotated with '@Rule' should be public">x</error>;

        @org.junit.Rule
        public static SomeTestRule <error descr="Field 'y' annotated with '@Rule' should be non-static">y</error>;
      }
    """.trimIndent())
    }
    fun `test malformed rule field non TestRule type highlighting`() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class RuleTest {
        @org.junit.Rule
        public int <error descr="Field 'x' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">x</error>;
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
        public static SomeTestRule <error descr="Method 'y' annotated with '@Rule' should be non-static">y</error>() { 
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
        static SomeTestRule <error descr="Field 'x' annotated with '@ClassRule' should be public">x</error> = new SomeTestRule();

        @org.junit.ClassRule
        public SomeTestRule <error descr="Field 'y' annotated with '@ClassRule' should be static">y</error> = new SomeTestRule();

        @org.junit.ClassRule
        private SomeTestRule <error descr="Field 'z' annotated with '@ClassRule' should be static and public">z</error> = new SomeTestRule();

        @org.junit.ClassRule
        public static int <error descr="Field 't' annotated with '@ClassRule' should be of type 'org.junit.rules.TestRule'">t</error> = 0;
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
        void <error descr="Method 'testOne' should be public, non-static, have no parameters and of type void">testOne</error>() { }
        public int <error descr="Method 'testTwo' should be public, non-static, have no parameters and of type void">testTwo</error>() { return 2; }
        public static void <error descr="Method 'testThree' should be public, non-static, have no parameters and of type void">testThree</error>() { }
        public void <error descr="Method 'testFour' should be public, non-static, have no parameters and of type void">testFour</error>(int i) { }
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
        @org.junit.Test void <error descr="Method 'testOne' annotated with '@Test' should be public">testOne</error>() {}
        @org.junit.Test public int <error descr="Method 'testTwo' annotated with '@Test' should be of type 'void'">testTwo</error>() { return 2; }
        @org.junit.Test public static void <error descr="Method 'testThree' annotated with '@Test' should be non-static">testThree</error>() {}
        @org.junit.Test public void <error descr="Method 'testFour' annotated with '@Test' should not declare parameter 'i'">testFour</error>(int i) {}
        @org.junit.Test public void testFive() {}
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
          public int <error descr="Method 'testMe' annotated with '@Test' should be of type 'void' and not declare parameter 'i'">testMe</error>(int i) { return -1; }
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


    // Unconstructable test case
    fun testPlain() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Plain { }
    """.trimIndent())
    }
    fun testUnconstructableJUnit3TestCase1() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class <error descr="Test class 'UnconstructableJUnit3TestCase1' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase1</error> extends TestCase {
          private UnconstructableJUnit3TestCase1() {
              System.out.println("");
          }
      }

    """.trimIndent())
    }
    fun testUnconstructableJUnit3TestCase2() {
      myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;

      public class <error descr="Test class 'UnconstructableJUnit3TestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase2</error> extends TestCase {
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
      
      public class <error descr="Test class 'UnconstructableJUnit4TestCase1' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase1</error> {
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

      class <error descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it is not 'public'"><error descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase3</error></error> {
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
  }
}