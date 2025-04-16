// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.deadCode

import com.intellij.junit.testFramework.JUnit5ImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJunit5ImplicitUsageProviderTest : JUnit5ImplicitUsageProviderTestBase() {
  fun `test method source inner annotation`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.lang.annotation.*;
      import java.util.stream.*;
      import org.junit.jupiter.params.provider.*;
      import org.junit.jupiter.params.ParameterizedTest;
      class MyTest {
        public static Stream<Arguments> allJdks() {
          return IntStream.of(8, 11).mapToObj(Arguments::of);
        }

        @TestAllJdks
        public void my(int jdk) {
          System.out.println("testing with " + jdk);
        }
        
        @ParameterizedTest(name = "jdk {0}")
        @MethodSource("allJdks")
        @Retention(RetentionPolicy.RUNTIME)
        @interface TestAllJdks {
        }
      }
    """)
  }

  fun `test method source outer annotation`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.lang.annotation.*;
      import java.util.stream.*;
      import org.junit.jupiter.params.provider.*;
      import org.junit.jupiter.params.ParameterizedTest;
      class MyTest {
        public static Stream<Arguments> allJdks() {
          return IntStream.of(8, 11).mapToObj(Arguments::of);
        }

        @TestAllJdks
        public void my(int jdk) {
          System.out.println("testing with " + jdk);
        }
        
        @ParameterizedTest(name = "jdk {0}")
        @MethodSource("allJdks")
        @Retention(RetentionPolicy.RUNTIME)
        @interface TestAllJdks {
        }
      }
    """)
  }

  fun `test method source direct link`() {
    myFixture.addClass("""
      package org.example;
      
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      
      @ParameterizedTest(name = "jdk {0}")
      @MethodSource("org.implementation.MyTest${'$'}NewTest#allJdks") 
      @Retention(RetentionPolicy.RUNTIME)
      public @interface TestAllJdks {
      }
    """.trimIndent())

    val clazz = myFixture.addClass("""
      package org.implementation;
      
      import org.junit.jupiter.params.provider.Arguments;
      import java.util.stream.IntStream;
      import java.util.stream.Stream;

      public class MyTest {      
          @org.example.TestAllJdks
          public void test(int jdk) {
              new NewTest();
              System.out.println("testing with " + jdk);
          }

          public static final class NewTest {
            public static Stream<Arguments> allJdks() {
                return IntStream.of(8, 11).mapToObj(Arguments::of);
            }
          }
      }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(clazz.containingFile.virtualFile)
    myFixture.checkHighlighting()
  }

  fun `test method source missing link`() {
    myFixture.addClass("""
      package org.example;
      
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;

      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      
      @ParameterizedTest(name = "jdk {0}")
      @MethodSource("#allJdks") 
      @Retention(RetentionPolicy.RUNTIME)
      public @interface TestAllJdks {
      }
    """.trimIndent())

    val clazz = myFixture.addClass("""
      package org.implementation;
      
      import org.junit.jupiter.params.provider.Arguments;
      import java.util.stream.IntStream;
      import java.util.stream.Stream;

      public class MyTest {
          public static void main(String[] args){
            new MyTest();
          }
          public static Stream<Arguments> <warning descr="Method 'allJdks()' is never used">allJdks</warning>() {
            return IntStream.of(8, 11).mapToObj(Arguments::of);
          }
      }
    """.trimIndent())

    myFixture.configureFromExistingVirtualFile(clazz.containingFile.virtualFile)
    myFixture.checkHighlighting()
  }

  fun `test implicit usage of enum source`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
        enum HostnameVerification {
          YES, NO;
        }
        
        @org.junit.jupiter.params.provider.EnumSource(HostnameVerification.class)
        @org.junit.jupiter.params.ParameterizedTest
        void testHostNameVerification(HostnameVerification hostnameVerification) {
          System.out.println(hostnameVerification);
        }
      }
    """.trimIndent())
  }

  fun `test implicit usage of parameter in parameterized test`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class MyTest {
        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        void byName(String name) {}
      }
   """.trimIndent())
  }

  fun `test implicit usage of method source with implicit method name`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.stream.*;
      
      class MyTest {
        @org.junit.jupiter.params.provider.MethodSource
        @org.junit.jupiter.params.ParameterizedTest
        void foo(String input) {
          System.out.println(input);
        }
  
        private static Stream<String> foo() {
            return Stream.of("");
        }      
      }
    """.trimIndent())
  }

  fun `test implicit usage of TempDir as direct annotation`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class Test {
          @org.junit.jupiter.api.io.TempDir
          private java.nio.file.Path tempDir;
            
          @org.junit.jupiter.api.Test
          void test() { 
            System.out.println(tempDir); 
          }
      }
      
    """.trimIndent())
  }

  fun `test implicit usage of TempDir as meta annotation`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.lang.annotation.*;
            
      class Test {
          @Target({ ElementType.FIELD, ElementType.PARAMETER })
          @Retention(RetentionPolicy.RUNTIME)
          @org.junit.jupiter.api.io.TempDir
          @interface CustomTempDir { }
          
          @CustomTempDir
          private java.nio.file.Path tempDir;
            
          @org.junit.jupiter.api.Test
          void test() { 
            System.out.println(tempDir); 
          }
      }
      
    """.trimIndent())
  }
}