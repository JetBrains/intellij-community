// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion.builder;

import com.intellij.psi.OriginInfoAwareElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;

public class MiscBuilderTransformationTest extends LightGroovyTestCase {
  public void test_resolve_inner_class_from_java() {
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
    PsiElement resolved = getFile().findReferenceAt(myFixture.getCaretOffset()).resolve();
    assertTrue(resolved instanceof OriginInfoAwareElement);
    assertTrue(resolved instanceof LightElement);
    assertEquals(BuilderAnnotationContributor.ORIGIN_INFO, ((OriginInfoAwareElement)resolved).getOriginInfo());
  }

  public void test_find_class() {
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
    myFixture.checkHighlighting();

    PsiClass clazz = myFixture.findClass("sample.Bean.BeanBuilder");

    assertTrue(clazz instanceof OriginInfoAwareElement);
    assertTrue(clazz instanceof LightElement);
    assertEquals(BuilderAnnotationContributor.ORIGIN_INFO, ((OriginInfoAwareElement)clazz).getOriginInfo());
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
