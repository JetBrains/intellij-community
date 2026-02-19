// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.junit.testFramework.JUnit5ReferenceContributorTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.psi.PsiMethod

class JavaJUnit5ReferenceContributorTest : JUnit5ReferenceContributorTestBase() {
  fun `test resolve to source method`() {
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      class ParameterizedTestsDemo {
         @ParameterizedTest
         @MethodSource(value = {"cde", "ab<caret>c"})
         void testWithProvider(String abc) {}
         
         private static List<String> abc() { return Arrays.asList("something1", "something2"); }
         private static List<String> cde() { return Arrays.asList("something1", "something2"); }
      }
    """.trimIndent()) { reference, _ ->
      assertContainsElements(reference.lookupStringVariants(), "abc")
    }
  }

  fun `test filter resolved to source method`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      interface MyFirstInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MySecondInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      abstract class MyAbstractClass implements MyFirstInterface, MySecondInterface {}
      
      class ParameterizedTestsDemo extends MyAbstractClass {
         @ParameterizedTest
         @MethodSource("ab<caret>c")
         void testWithProvider(String abc) {}
      }
    """.trimIndent()) { _, results ->
      assertEquals(1, results.size)
      val resolved = results.first().element
      assertTrue(resolved is PsiMethod)
      if (resolved !is PsiMethod) return@assertMultiresolveReference
      assertEquals("MyFirstInterface", resolved.containingClass?.name)
      assertEquals("abc", resolved.name)
    }
  }

  fun `test filter resolved to source method from first interface`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      interface MyFirstInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MySecondInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      class ParameterizedTestsDemo implements MyFirstInterface, MySecondInterface {
         @ParameterizedTest
         @MethodSource("ab<caret>c")
         void testWithProvider(String abc) {}
      }
    """.trimIndent()) { _, results ->
      assertEquals(1, results.size)
      val resolved = results.first().element
      assertTrue(resolved is PsiMethod)
      if (resolved !is PsiMethod) return@assertMultiresolveReference
      assertEquals("MyFirstInterface", resolved.containingClass?.name)
      assertEquals("abc", resolved.name)
    }
  }

  fun `test filter resolved to source method from abstract class`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      interface MyInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      abstract class MyAbstractClass implements MyInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
            
      class ParameterizedTestsDemo extends MyAbstractClass {
         @ParameterizedTest
         @MethodSource("ab<caret>c")
         void testWithProvider(String abc) {}
      }
    """.trimIndent()) { _, results ->
      assertEquals(1, results.size)
      val resolved = results.first().element
      assertTrue(resolved is PsiMethod)
      if (resolved !is PsiMethod) return@assertMultiresolveReference
      assertEquals("MyAbstractClass", resolved.containingClass?.name)
      assertEquals("abc", resolved.name)
    }
  }

  fun `test filter resolved to source method from deep interface hierarchy`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      interface MyInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MyFirstInterface extends MyInterface { }
      
      interface MySecondInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      abstract class MyAbstractClass implements MyFirstInterface, MySecondInterface { }
                  
      class ParameterizedTestsDemo extends MyAbstractClass {
         @ParameterizedTest
         @MethodSource("ab<caret>c")
         void testWithProvider(String abc) {}
      }
    """.trimIndent()) { _, results ->
      assertEquals(1, results.size)
      val resolved = results.first().element
      assertTrue(resolved is PsiMethod)
      if (resolved !is PsiMethod) return@assertMultiresolveReference
      assertEquals("MyInterface", resolved.containingClass?.name)
      assertEquals("abc", resolved.name)
    }
  }

  fun `test filter resolved to source method with inherited`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;

      interface MyFirstInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MySecondInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
            
      class ParameterizedTestsDemo implements MySecondInterface, MyFirstInterface {
         @ParameterizedTest
         @MethodSource("ab<caret>c")
         void testWithProvider(String abc) {}
      }
      
      class ChildOfParameterizedTestsDemo extends ParameterizedTestsDemo {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
    """.trimIndent()) { _, results ->
      val classes = results.map { (it.element as PsiMethod).containingClass?.name }.toSet()
      assertEquals(setOf("MySecondInterface", "ChildOfParameterizedTestsDemo"), classes)
    }
  }

  fun `test filter resolved to source method with meta-annotation`() {
    myFixture.assertMultiresolveReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      import java.util.Arrays;
      import java.util.List;
      
      interface MyFirstInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MySecondInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      interface MyThirdInterface {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
      
      @ParameterizedTest
      @MethodSource("ab<caret>c")
      @interface MyAnnotation {}
            
      class ParameterizedTestsDemo1 implements MySecondInterface, MyFirstInterface {
         @MyAnnotation
         void testWithProvider(String abc) {}
      }
      
      class ParameterizedTestsDemo2 implements MyThirdInterface, MyFirstInterface {
         @MyAnnotation
         void testWithProvider(String abc) {}
      }

      class ChildOfParameterizedTestsDemo extends ParameterizedTestsDemo1 {
        static List<String> abc() {return Arrays.asList("something1", "something2");}
      }
    """.trimIndent()) { _, results ->
      val classes = results.map { (it.element as PsiMethod).containingClass?.name }.toSet()
      assertEquals(setOf("MySecondInterface", "MyThirdInterface", "ChildOfParameterizedTestsDemo"), classes)
    }
  }

  fun `test resolve to source field`() {
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.FieldSource;
      
      import java.util.Arrays;
      import java.util.List;
      
      class ParameterizedFieldSourceTestsDemo {
         @ParameterizedTest
         @FieldSource(value = {"aaa", "bb<caret>b2"})
         void testWithProvider(String abc) {}
         
         static final List<String> aaa = Arrays.asList("something1", "something2");
         static final List<String> bbb2 = Arrays.asList("something1", "something2");
      }
    """.trimIndent())
  }

  fun `test resolve to enum source`() {
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.EnumSource;
      
      private enum Foo { AAA, AAX, BBB }
      
      class ResolveEnumSource {
        @ParameterizedTest
        @EnumSource(value = Foo.class, names = "AA<caret>A", mode = EnumSource.Mode.EXCLUDE)
        void single() { }
      }""".trimIndent())
  }

  fun `test resolve enum source with unsupported mode`() {
    myFixture.assertUnResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.EnumSource;
      
      private enum Foo { AAA, AAX, BBB }
      
      class ResolveEnumSource {
        @ParameterizedTest
        @EnumSource(value = Foo.class, names = "AA<caret>A", mode = EnumSource.Mode.MATCH_ALL)
        void single() { } 
      }""".trimIndent()) { reference -> assertDoesntContain(reference.lookupStringVariants(), "AAA", "AAX", "BBB") }
  }

  fun `test resolve enum source with default mode`() {
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.EnumSource;
      
      private enum Foo { AAA, AAX, BBB }
      
      class ResolveEnumSource {
        @ParameterizedTest
        @EnumSource(value = Foo.class, names = "AA<caret>A")
        void single() { } 
      }""".trimIndent())
  }

  fun `test resolve enum source without mode`() {
    myFixture.addClass("""
      package org.junit.jupiter.params.provider;
      
      public @interface EnumSource { 
        Class<? extends Enum<?>> value(); 
        String[] names() default { };
      }
    """.trimIndent())
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.EnumSource;
      
      private enum Foo { AAA, AAX, BBB }
      
      class ResolveEnumSource {
        @ParameterizedTest
        @EnumSource(value = Foo.class, names = "AA<caret>A")
        void single() { }
      }
    """.trimIndent())
  }

  fun `test unresolvable enum source because of invalid name`() {
    myFixture.assertUnResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.EnumSource;
      
      private enum Foo { AAA, AAX, BBB }
      
      class ResolveEnumSourceInvalidValue {
        @ParameterizedTest
        @EnumSource(value = Foo.class, names = "invalid<caret>value")
        void single() { }
      }
    """.trimIndent()) { reference -> assertContainsElements(reference.lookupStringVariants(), "AAA", "AAX", "BBB") }
  }
}
