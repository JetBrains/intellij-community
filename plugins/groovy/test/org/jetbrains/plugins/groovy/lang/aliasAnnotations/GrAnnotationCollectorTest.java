// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.aliasAnnotations;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Max Medvedev
 */
public class GrAnnotationCollectorTest extends LightGroovyTestCase {
  public void testAnnotatedAlias() {
    doTest("""
             import groovy.transform.*
             
             @AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
             @interface Alias {}
             
             @Alias(excludes = ["a"])
             class F<caret>oo {
                 Integer a, b
             }
             """, """
             @ToString(excludes = ["a"])
             @EqualsAndHashCode(excludes = ["a"])
             @Immutable
             """);
  }

  public void testAliasWithProperties() {
    doTest("""
             import groovy.transform.*
             
             @AnnotationCollector([ToString, EqualsAndHashCode, Immutable])
             @interface Alias {}
             
             @Alias(excludes = ["a"])
             class F<caret>oo {
                 Integer a, b
             }
             
             """, """
             @ToString(excludes = ["a"])
             @EqualsAndHashCode(excludes = ["a"])
             @Immutable
             """);
  }

  public void testMixedProperties() {
    doTest("""
             import groovy.transform.*
             
             @ToString(excludes = ['a', 'b'])
             @AnnotationCollector([EqualsAndHashCode, Immutable])
             @interface Alias {}
             
             @Alias(excludes = ["a"])
             class F<caret>oo {
                 Integer a, b
             }
             """, """
             @EqualsAndHashCode(excludes = ["a"])
             @Immutable
             @ToString(excludes = ["a"])
             """);
  }

  public void testAliasDeclarationWithoutParams() {
    doTest("""
             @interface X {}
             @interface Y {}
             
             @X @Y
             @groovy.transform.AnnotationCollector
             @interface Alias {}
             
             @Alias
             class F<caret>oo {}
             """, """
             @X
             @Y
             """);
  }

  public void doTest(@NotNull String text, @NotNull String expectedAnnotations) {
    addAnnotationCollector();
    myFixture.configureByText("_a.groovy", text);
    final PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
    final GrTypeDefinition clazz = PsiTreeUtil.getParentOfType(atCaret, GrTypeDefinition.class);
    TestCase.assertNotNull(clazz);

    final String actual = getActualAnnotations(clazz);
    TestCase.assertEquals(expectedAnnotations, actual);
  }

  @NotNull
  @NonNls
  public static String getActualAnnotations(@NotNull GrTypeDefinition clazz) {
    StringBuilder buffer = new StringBuilder();
    for (GrAnnotation annotation : clazz.getModifierList().getAnnotations()) {
      buffer.append("@").append(annotation.getShortName());
      final GrAnnotationNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      if (attributes.length > 0) {
        buffer.append("(");
        for (GrAnnotationNameValuePair pair : attributes) {
          final String name = pair.getName();
          if (name != null) {
            buffer.append(name).append(" = ");
          }
          buffer.append(pair.getValue().getText());
        }
        buffer.append(")");
      }
      buffer.append("\n");
    }
    return buffer.toString();
  }

  public void test_recursive_alias() {
    getFixture().configureByText("_.groovy", """
      @Alias
      @groovy.transform.AnnotationCollector
      @interface Alias {}
      
      @Alias
      class <caret>Usage {}
      """);
    getFixture().checkHighlighting();
    assertEmpty(getActualAnnotations((GrTypeDefinition)getFixture().findClass("Usage")));
  }
}
