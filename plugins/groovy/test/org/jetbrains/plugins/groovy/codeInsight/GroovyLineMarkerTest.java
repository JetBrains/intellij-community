// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class GroovyLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testOverride() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.groovy", """
      class Test{
        void m(){}
      }
      
      class Child extends Test{
        void m(){}
      }
      static void main(String[] args) {
        println "Hello world!"
      }""");
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(4, gutters.size());
  }

  @Test
  public void testGuttersInDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Main.groovy", """
      class Test{
        void m(){}
      }
    
      class Child extends Test{
        void m(){}
      }
      static void main(String[] args) {
        println "Hello world!"
      }""");
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      GroovyLineMarkerProvider provider = new GroovyLineMarkerProvider();
      Set<LineMarkerInfo> result = new HashSet<>();
      ReadAction.runBlocking(() -> {
        List<PsiMethod> methods = List.copyOf(PsiTreeUtil.findChildrenOfType(file, PsiMethod.class));
        provider.collectSlowLineMarkers(methods, result);
      });
      assertEquals(0, result.size());
    });
  }
}
