// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.OriginInfoAwareElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class MiscBuilderTransformationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void test_resolve_inner_class_from_java() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("sample/Bean.groovy", """
      package sample
      
      @groovy.transform.builder.Builder
      class Bean {
          String prop1
          Integer prop2
      }
      """);
    myFixture.configureByText("JavaConsumer.java", """
      import sample.Bean;
      
      class JavaConsumer {
          void foo(Bean.Bean<caret>Builder b) {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiElement resolved = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()).resolve();
      assertTrue(resolved instanceof OriginInfoAwareElement);
      assertTrue(resolved instanceof LightElement);
      assertEquals(BuilderAnnotationContributor.ORIGIN_INFO, ((OriginInfoAwareElement)resolved).getOriginInfo());
    });
  }

  @Test
  public void test_find_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("sample/Bean.groovy", """
      package sample
      
      @groovy.transform.builder.Builder
      class Bean {
          String prop1
          Integer prop2
      }
      """);
    myFixture.configureByText("JavaConsumer.java", """
      import sample.Bean;
      
      class JavaConsumer {
          void main() {
              Bean b = Bean.builder().prop1("").prop2(1).build();
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkHighlighting();

      PsiClass clazz = myFixture.findClass("sample.Bean.BeanBuilder");

      assertTrue(clazz instanceof OriginInfoAwareElement);
      assertTrue(clazz instanceof LightElement);
      assertEquals(BuilderAnnotationContributor.ORIGIN_INFO, ((OriginInfoAwareElement)clazz).getOriginInfo());
    });
  }
}
