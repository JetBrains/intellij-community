// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;

public class RefactoringFieldTest extends AbstractJavaFXTestCase {

  public void testPropertyRename() {
    myFixture.configureByFile(getTestName(false) + ".java");
    performRename("newName");
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testPropertyDelete() {
    myFixture.configureByFile(getTestName(false) + ".java");
    performDelete();
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  protected void performRename(String newName) {
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil
                                                                                      .ELEMENT_NAME_ACCEPTED |
                                                                                    TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    RenameRefactoring refactoring = RefactoringFactory.getInstance(getProject()).createRename(element, newName, false, false);
    refactoring.respectAllAutomaticRenames();
    refactoring.run();
  }

   private void performDelete() {
    final PsiElement psiElement = TargetElementUtil
      .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull("No element found in text:\n" + myFixture.getFile().getText(), psiElement);
    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{psiElement}, true);
  }
  
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/fieldRefactoring/";
  }
}
