// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class TestDataRelatedItem extends GotoRelatedItem {
  private final List<TestDataFile> myTestDataFiles;
  private final Editor myEditor;

  public TestDataRelatedItem(@NotNull PsiElement location, @NotNull Editor editor, @NotNull List<TestDataFile> testDataFiles) {
    super(location, "Test Data");
    myEditor = editor;
    myTestDataFiles = testDataFiles;
  }

  @Override
  public String getCustomName() {
    return myTestDataFiles.size() != 1
           ? "Test Data"
           : myTestDataFiles.get(0).getName();
  }

  @Override
  public void navigate() {
    RelativePoint location = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
    TestDataNavigationHandler.navigate(location, myTestDataFiles, Objects.requireNonNull(getElement()).getProject());
  }
}
