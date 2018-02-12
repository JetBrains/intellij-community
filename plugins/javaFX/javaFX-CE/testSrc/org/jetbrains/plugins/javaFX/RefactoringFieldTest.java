/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
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

    new RenameProcessor(getProject(), element, newName, false, false).run();
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
