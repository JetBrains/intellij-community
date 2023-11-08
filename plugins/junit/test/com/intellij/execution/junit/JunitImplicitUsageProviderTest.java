// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JunitImplicitUsageProviderTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.enableInspections(new UnusedDeclarationInspection(true));

    myFixture.addClass("""
                         package java.nio.file;
                         public interface Path {}
                         """);
    myFixture.addClass("""
                         package org.assertj.core.api;
                         import java.nio.file.Path;
                         public final class Assertions { public static void assertThat(Path actual) {} }
                         """);
  }

  public void testRecognizeTestMethodInParameterizedClass() {
    myFixture.configureByText("Test.java", """
      import org.junit.jupiter.params.provider.EnumSource;

      class Test {
        enum HostnameVerification {
          YES, NO;
        }
         @EnumSource(HostnameVerification.class)
         static void test(HostnameVerification hostnameVerification) {
              System.out.println(hostnameVerification);
          }
          public static void main(String[] args) {
              test(null);
          }
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testParameterizedDisplayNameByParameter() {
    myFixture.configureByText("MyTest.java", """
      import org.junit.jupiter.params.ParameterizedTest;class MyTest {
      @ParameterizedTest(name = "{0}")
      void byName(String name) {}
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testJunitTempDirMetaAnnotation() {
    myFixture.configureByText("Test.java", """
      import org.junit.jupiter.api.io.TempDir;
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Retention;
      import java.lang.annotation.RetentionPolicy;
      import java.lang.annotation.Target;
      import java.nio.file.Path;
            
      import static org.assertj.core.api.Assertions.assertThat;
            
      class Test {
          @Target({ ElementType.FIELD, ElementType.PARAMETER })
          @Retention(RetentionPolicy.RUNTIME)
          @TempDir
          @interface CustomTempDir {
          }
          
          @CustomTempDir
          private Path tempDir;
            
          @org.junit.jupiter.api.Test
          void test() {
              assertThat(tempDir);
          }
      }
      """);
    myFixture.testHighlighting(true, false, false);
  }
}
