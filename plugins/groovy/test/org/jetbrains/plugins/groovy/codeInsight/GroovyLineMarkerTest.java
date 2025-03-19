// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GroovyLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testOverride() {
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

  public void testGuttersInDumbMode() {
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
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      GroovyLineMarkerProvider provider = new GroovyLineMarkerProvider();
      Set<LineMarkerInfo> result = new HashSet<>();
      List<PsiMethod> methods = List.copyOf(PsiTreeUtil.findChildrenOfType(file, PsiMethod.class));
      provider.collectSlowLineMarkers(methods, result);
      assertEquals(0, result.size());
    });
  }
}
