// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TestDataRelatedItem extends GotoRelatedItem {
  private final List<String> myTestDataFiles;
  private final Editor myEditor;
  private final PsiMethod myMethod;

  public TestDataRelatedItem(@NotNull PsiMethod method, @NotNull Editor editor, @NotNull List<String> testDataFiles) {
    super(method, "Test Data");
    myMethod = method;
    myEditor = editor;
    myTestDataFiles = testDataFiles;
  }

  @Override
  public String getCustomName() {
    return myTestDataFiles.size() != 1
           ? "Test Data"
           : PathUtil.getFileName(myTestDataFiles.get(0));
  }

  @Override
  public void navigate() {
    TestDataNavigationHandler.navigate(
      JBPopupFactory.getInstance().guessBestPopupLocation(myEditor), myTestDataFiles, myMethod.getProject()
    );
  }
}
