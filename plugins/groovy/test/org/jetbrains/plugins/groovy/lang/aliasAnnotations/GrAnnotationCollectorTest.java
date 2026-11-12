// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.aliasAnnotations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyFixtures;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Max Medvedev
 */
@TestApplication
public class GrAnnotationCollectorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
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

  @Test
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

  @Test
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

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LightGroovyFixtures.addAnnotationCollector(myFixture);
    myFixture.configureByText("_a.groovy", text);
    ReadAction.runBlocking(() -> {
      final PsiElement atCaret = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final GrTypeDefinition clazz = PsiTreeUtil.getParentOfType(atCaret, GrTypeDefinition.class);
      assertNotNull(clazz);

      final String actual = getActualAnnotations(clazz);
      assertEquals(expectedAnnotations, actual);
    });
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

  @Test
  public void test_recursive_alias() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      @Alias
      @groovy.transform.AnnotationCollector
      @interface Alias {}
      
      @Alias
      class <caret>Usage {}
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkHighlighting();
      assertThat(getActualAnnotations((GrTypeDefinition)myFixture.findClass("Usage"))).isNullOrEmpty();
    });
  }
}
