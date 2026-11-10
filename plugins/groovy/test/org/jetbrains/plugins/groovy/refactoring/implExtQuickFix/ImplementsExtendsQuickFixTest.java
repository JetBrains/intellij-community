// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.implExtQuickFix;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * User: Dmitry.Krasilschikov
 */
@TestApplication
public class ImplementsExtendsQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "groovy/extendsImplementsFix/");
  }

  @Test
  public void testClass1() {
    doTest();
  }

  @Test
  public void testExt1() {
    doTest();
  }

  @Test
  public void testImpl1() {
    doTest();
  }

  @Test
  public void testImpl2() {
    doTest();
  }

  @Test
  public void testImplext1() {
    doTest();
  }

  @Test
  public void testImplExt2() {
    doTest();
  }

  @Test
  public void testInterface1() {
    doTest();
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final List<String> data = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test");
    String fileText = data.get(0);
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(myFixture.getProject(), fileText);
      assertInstanceOf(GroovyFileBase.class, psiFile);
      final GrTypeDefinition[] typeDefinitions = ((GroovyFileBase)psiFile).getTypeDefinitions();
      final GrTypeDefinition typeDefinition = typeDefinitions[typeDefinitions.length - 1];
      String newText;
      if (typeDefinition.getImplementsClause() == null && typeDefinition.getExtendsClause() == null) {
        newText = "";
      }
      else {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            ChangeExtendsImplementsQuickFix fix = new ChangeExtendsImplementsQuickFix(typeDefinition);
            fix.asIntention().invoke(myFixture.getProject(), null, psiFile);
            UsefulTestCase.doPostponedFormatting(myFixture.getProject());
          });

        final GrTypeDefinition[] newTypeDefinitions = ((GroovyFileBase)psiFile).getTypeDefinitions();
        newText = newTypeDefinitions[newTypeDefinitions.length - 1].getText();
      }

      assertEquals(data.get(1), newText);
    });
  }
}
