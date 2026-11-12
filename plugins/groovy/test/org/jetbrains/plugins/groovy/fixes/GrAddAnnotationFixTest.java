// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrAddAnnotationFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @Test
  public void testAddAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    @Language("Groovy")
    String text = """
    class Cls {
      static String s() {
        return "hello"
      }
    }
    """;
    myFixture.addClass("package anno;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface Anno { }");
    myFixture.configureByText("Cls.groovy", text);
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiMethod method = ((PsiClassOwner)myFixture.getFile()).getClasses()[0].getMethods()[0];
      WriteCommandAction.runWriteCommandAction(
        myFixture.getProject(),
        (Computable<PsiAnnotation>)() -> AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent("anno.Anno", PsiNameValuePair.EMPTY_ARRAY,
                                                                                           method.getModifierList()));
      myFixture.checkResult("""
                              import anno.Anno
                              
                              class Cls {
                                @Anno
                                static String s() {
                                  return "hello"
                                }
                              }
                              """);
    });
  }
}