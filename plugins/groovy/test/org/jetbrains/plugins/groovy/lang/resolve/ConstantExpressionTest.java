// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@TestApplication
public class ConstantExpressionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  private void addJavaAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                            package com.foo;
                            
                            public @interface MyJavaAnnotation {
                                String[] stringArrayValue() default {};
                                String stringValue() default "default string";
                            }
                            """);
  }

  private void addJavaConstants() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                            package com.foo;
                            public interface Constants {
                              String HELLO = "java hello";
                              String WORLD = "java world";
                              String COMPOUND = HELLO + " " + WORLD;
                            }
                            """);
  }

  @Test
  public void test_annotation_value_from_java() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaAnnotation();
    addJavaConstants();
    myFixture.addFileToProject("_.groovy", """
      import com.foo.Constants
      import com.foo.MyJavaAnnotation
      
      @MyJavaAnnotation(stringArrayValue = [
              Constants.HELLO,
              Constants.WORLD,
              Constants.COMPOUND,
              "literal"
      ])
      class GroovyClass {}
      """);
    ReadAction.runBlocking(() -> {
      TestUtils.disableAstLoading(myFixture.getProject(), myFixture.getTestRootDisposable());
      PsiClass clazz = myFixture.findClass("GroovyClass");
      PsiAnnotation annotation = clazz.getModifierList().findAnnotation("com.foo.MyJavaAnnotation");
      List<String> values = GrAnnotationUtil.getStringArrayValue(annotation, "stringArrayValue", false);
      assertIterableEquals(values, List.of("java hello", "java world", "java hello java world", "literal"));
    });
  }
}
