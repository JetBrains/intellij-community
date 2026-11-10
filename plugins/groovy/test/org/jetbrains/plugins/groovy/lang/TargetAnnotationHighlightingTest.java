// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class TargetAnnotationHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private void addElementType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                          package java.lang.annotation;

                          public enum ElementType {
                              /** Class, interface (including annotation type), or enum declaration */
                              TYPE,

                              /** Field declaration (includes enum constants) */
                              FIELD,

                              /** Method declaration */
                              METHOD,

                              /** Parameter declaration */
                              PARAMETER,

                              /** Constructor declaration */
                              CONSTRUCTOR,

                              /** Local variable declaration */
                              LOCAL_VARIABLE,

                              /** Annotation type declaration */
                              ANNOTATION_TYPE,

                              /** Package declaration */
                              PACKAGE
                          }
                          """);
  }

  private void addTarget() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package java.lang.annotation;

                         public @interface Target {
                             ElementType[] value();
                         }
                         """);
  }

  @Test
  public void testTargetAnnotationInsideGroovy1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addElementType();
    addTarget();
    myFixture.addFileToProject("Ann.groovy", """
      import java.lang.annotation.Target

      import static java.lang.annotation.ElementType.*

      @Target(FIELD)
      @interface Ann {}
      """);

    myFixture.configureByText("_.groovy", """
      @<error descr="'@Ann' annotation not allowed on type">Ann</error>
      class C {
        @Ann
        def foo

        def ar() {
          @<error descr="'@Ann' annotation not allowed on local variable">Ann</error>
          def x
        }
      }""");

    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testTargetAnnotationInsideGroovy2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addElementType();
    addTarget();
    myFixture.addFileToProject("Ann.groovy", """
      import java.lang.annotation.Target

      import static java.lang.annotation.ElementType.*

      @Target(value=[FIELD, TYPE])
      @interface Ann {}
      """);

    myFixture.configureByText("_.groovy", """
      @Ann
      class C {
        @Ann
        def foo

        def ar() {
          @<error descr="'@Ann' annotation not allowed on local variable">Ann</error>
          def x
        }
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Test
  public void testTargetAnnotationInsideGroovy3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addElementType();
    addTarget();
    myFixture.addFileToProject("Ann.groovy", """
      import java.lang.annotation.Target

      import static java.lang.annotation.ElementType.*

      @Target(LOCAL_VARIABLE)
      @interface Ann {}
      """);

    myFixture.configureByText("_.groovy", """
      @<error descr="'@Ann' annotation not allowed on type">Ann</error>
      class C {
        @<error descr="'@Ann' annotation not allowed on field">Ann</error>
        def foo

        def ar() {
          @Ann
          def x
        }
      }""");
    myFixture.testHighlighting(true, false, false);
  }
}
