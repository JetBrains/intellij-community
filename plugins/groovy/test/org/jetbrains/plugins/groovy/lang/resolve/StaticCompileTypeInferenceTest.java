// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.Assert;

public class StaticCompileTypeInferenceTest extends TypeInferenceTestBase {
  public void testExplicitFieldType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            String aa = "i'm string"
            def foo() { a<caret>a }
        }
        """).getElement();
    final PsiType type = ref.getType();
    Assert.assertTrue(type instanceof PsiClassType);
    Assert.assertEquals(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
  }

  public void testImplicitWithInstanceofType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
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
    final PsiType type = ref.getType();
    Assert.assertTrue(type instanceof PsiClassType);
    Assert.assertEquals(CommonClassNames.JAVA_LANG_STRING, type.getCanonicalText());
  }

  public void testExplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            Object aa = "i'm string"
            def foo() { a<caret>a }
        }
        """).getElement();
    final PsiType type = ref.getType();
    Assert.assertTrue(type instanceof PsiClassType);
    Assert.assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
  }

  public void testImplicitObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        class Foo {
            def aa
            def foo() { a<caret>a }
        }
        """).getElement();
    final PsiType type = ref.getType();
    Assert.assertTrue(type instanceof PsiClassType);
    Assert.assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
  }

  public void testEnumObjectType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
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
    final PsiType type = ref.getType();
    UsefulTestCase.assertInstanceOf(type, PsiClassType.class);
    Assert.assertEquals("Foo.E", type.getCanonicalText());
  }

  public void testImplicitObjectTypeWithInitializer() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        import groovy.transform.CompileStatic
        
        class Foo {
            def aa = 1
            @CompileStatic
            def foo() { a<caret>a }
        }
        """).getElement();
    final PsiType type = ref.getType();
    Assert.assertTrue(type instanceof PsiClassType);
    Assert.assertEquals(CommonClassNames.JAVA_LANG_OBJECT, type.getCanonicalText());
  }

  public void testImplicitParameterType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        import groovy.transform.CompileStatic
        
        class Foo {
            def foo(aa) { a<caret>a }
        }
        """).getElement();
    final PsiType type = ref.getType();
    Assert.assertNull(type);
  }

  public void testVariableType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByText(
      """
        @groovy.transform.CompileStatic
        class Foo {
          def foo() {
            List<?> ll = ["a"]
            l<caret>l
          }
        }
        """).getElement();
    Assert.assertEquals("java.util.List<java.lang.String>", ref.getType().getCanonicalText());
  }
}
