// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.junit.testFramework.JUnit5ReferenceContributorTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJUnit5ReferenceContributorTest : JUnit5ReferenceContributorTestBase() {
  fun `test resolve to source method`() {
    myFixture.assertResolvableReference(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      
      class ParameterizedTestsDemo {
         @MethodSource(value = {"cde", "ab<caret>c"})
         void testWithProvider(String abc) {}
         
         private static void abc() {}
         
         private static void cde() {}
      }
    """.trimIndent()) { reference, resolved ->
      assertContainsElements(reference.lookupStringVariants(), "abc", "cde")
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
