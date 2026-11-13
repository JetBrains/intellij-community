// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFixture;
import org.junit.jupiter.api.Test;

import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxFixture;
import static org.jetbrains.plugins.javaFX.fxml.JavaFxFixtureKt.javaFxProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class RefactoringFieldTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFxProjectFixture();
  private final TestFixture<JavaFxFixture> javaFx =
    javaFxFixture(projectFixture, PluginPathManager.getPluginHomePath("javaFX") + "/testData/fieldRefactoring/");

  @Test
  public void testPropertyRename() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      performRename("newName");
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testPropertyDelete() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      performDelete();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  protected void performRename(String newName) {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil
                                                                                      .ELEMENT_NAME_ACCEPTED |
                                                                                    TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    RenameRefactoring refactoring = RefactoringFactory.getInstance(myFixture.getProject()).createRename(element, newName, false, false);
    refactoring.respectAllAutomaticRenames();
    refactoring.run();
  }

   private void performDelete() {
    LightJavaCodeInsightTestFixture myFixture = javaFx.get().getFixture();
    final PsiElement psiElement = TargetElementUtil
      .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(psiElement, "No element found in text:\n" + myFixture.getFile().getText());
    SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{psiElement}, true);
  }
}
