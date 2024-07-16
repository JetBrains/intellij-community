// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class TargetAnnotationHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  private void addElementType() {
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
    myFixture.addClass("""
                         package java.lang.annotation;

                         public @interface Target {
                             ElementType[] value();
                         }
                         """);
  }

  public void testTargetAnnotationInsideGroovy1() {
    addElementType();
    addTarget();
    myFixture.addFileToProject("Ann.groovy", """
      import java.lang.annotation.Target

      import static java.lang.annotation.ElementType.*

      @Target(FIELD)
      @interface Ann {}
      """);

    myFixture.configureByText("_.groovy", """
      @<error descr="'@Ann' not applicable to type">Ann</error>
      class C {
        @Ann
        def foo

        def ar() {
          @<error descr="'@Ann' not applicable to local variable">Ann</error>
          def x
        }
      }""");

    myFixture.testHighlighting(true, false, false);
  }

  public void testTargetAnnotationInsideGroovy2() {
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
          @<error descr="'@Ann' not applicable to local variable">Ann</error>
          def x
        }
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  public void testTargetAnnotationInsideGroovy3() {
    addElementType();
    addTarget();
    myFixture.addFileToProject("Ann.groovy", """
      import java.lang.annotation.Target

      import static java.lang.annotation.ElementType.*

      @Target(LOCAL_VARIABLE)
      @interface Ann {}
      """);

    myFixture.configureByText("_.groovy", """
      @<error descr="'@Ann' not applicable to type">Ann</error>
      class C {
        @<error descr="'@Ann' not applicable to field">Ann</error>
        def foo

        def ar() {
          @Ann
          def x
        }
      }""");
    myFixture.testHighlighting(true, false, false);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
  }
}
