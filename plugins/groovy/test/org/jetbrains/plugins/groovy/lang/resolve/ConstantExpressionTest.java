// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class ConstantExpressionTest extends LightGroovyTestCase {
  private void addJavaAnnotation() {
    getFixture().addClass("""
                            package com.foo;
                            
                            public @interface MyJavaAnnotation {
                                String[] stringArrayValue() default {};
                                String stringValue() default "default string";
                            }
                            """);
  }

  private void addJavaConstants() {
    getFixture().addClass("""
                            package com.foo;
                            public interface Constants {
                              String HELLO = "java hello";
                              String WORLD = "java world";
                              String COMPOUND = HELLO + " " + WORLD;
                            }
                            """);
  }

  public void test_annotation_value_from_java() {
    addJavaAnnotation();
    addJavaConstants();
    getFixture().addFileToProject("_.groovy", """
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
    TestUtils.disableAstLoading(getProject(), getTestRootDisposable());
    PsiClass clazz = getFixture().findClass("GroovyClass");
    PsiAnnotation annotation = clazz.getModifierList().findAnnotation("com.foo.MyJavaAnnotation");
    List<String> values = GrAnnotationUtil.getStringArrayValue(annotation, "stringArrayValue", false);
    UsefulTestCase.assertOrderedEquals(List.of("java hello", "java world", "java hello java world", "literal"),
                                       values);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
