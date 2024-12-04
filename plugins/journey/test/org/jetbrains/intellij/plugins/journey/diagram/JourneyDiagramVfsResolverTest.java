package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JourneyDiagramVfsResolverTest extends LightJavaCodeInsightFixtureTestCase5 {

  @Test
  void name() {
    String code = """
      package com.example;s
      public class Foo {
        public void bar() {}
      }
      """;
    PsiClass aClass = createClass(code);
    JourneyDiagramVfsResolver resolver = new JourneyDiagramVfsResolver(
      (__) -> null,
      (project) -> {
        JavaPsiFacade mock = Mockito.mock(JavaPsiFacade.class);
        Mockito.doAnswer(__ -> aClass).when(mock)
          .findClass(ArgumentMatchers.argThat(it -> it.equals("com.example.Foo")), ArgumentMatchers.any());
        return mock;
      }
    );

    String expectedFQN = "com.example.Foo#bar";
    JourneyNodeIdentity identity = resolver.resolveElementByFQN(expectedFQN, getFixture().getProject());
    assertNotNull(identity);
    assertEquals("Foo", identity.getMember().getContainingClass().getName());
    assertEquals("bar", identity.getMember().getName());
    assertEquals(expectedFQN, resolver.getQualifiedName(identity));
  }

  private PsiClass createClass(String javaCode) {
    return ReadAction.compute(() -> {
      return createDummyJavaFile(javaCode, getFixture().getProject()).getClasses()[0];
    });
  }

  protected PsiJavaFile createDummyJavaFile(String text, @NotNull Project project) {
    return ReadAction.compute(() -> {
      var myManager = PsiManager.getInstance(project);
      return (PsiJavaFile)PsiFileFactory.getInstance(myManager.getProject())
        .createFileFromText("_dummy_.java", JavaFileType.INSTANCE, text);
    });
  }
}
