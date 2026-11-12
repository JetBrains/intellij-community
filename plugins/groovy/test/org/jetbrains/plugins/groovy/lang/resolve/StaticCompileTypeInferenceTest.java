// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.TypeInferenceFixtureKt.typeInferenceFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class StaticCompileTypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<TypeInferenceFixture> inference = typeInferenceFixture(projectFixture);

  @Test
  public void testExplicitFieldType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            String aa = "i'm string"
            def foo() { a<caret>a }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
    });
  }

  @Test
  public void testImplicitWithInstanceofType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            def aa = 1
            def foo() {
              if (aa instanceof String) {
                a<caret>a
              }
           }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
    });
  }

  @Test
  public void testExplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            Object aa = "i'm string"
            def foo() { a<caret>a }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
    });
  }

  @Test
  public void testImplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            def aa
            def foo() { a<caret>a }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
    });
  }

  @Test
  public void testEnumObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
          static enum E {
            FF, g, h
          }
          def foo() {
            E.<caret>FF
          }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertInstanceOf(PsiClassType.class, type);
      assertEquals("Foo.E", type.getCanonicalText());
    });
  }

  @Test
  public void testImplicitObjectTypeWithInitializer() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        class Foo {
            def aa = 1
            @CompileStatic
            def foo() { a<caret>a }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
    });
  }

  @Test
  public void testImplicitParameterType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        import groovy.transform.CompileStatic
        
        class Foo {
            def foo(aa) { a<caret>a }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      final PsiType type = ref.getType();
      assertNull(type);
    });
  }

  @Test
  public void testVariableType() {
    final GrReferenceExpression ref = (GrReferenceExpression)inference.get().configureByText(
      """
        @groovy.transform.CompileStatic
        class Foo {
          def foo() {
            List<?> ll = ["a"]
            l<caret>l
          }
        }
        """).getElement();
    ReadAction.runBlocking(() -> {
      assertEquals("java.util.List<java.lang.String>", ref.getType().getCanonicalText());
    });
  }
}
