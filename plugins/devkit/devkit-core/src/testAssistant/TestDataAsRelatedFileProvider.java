// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.Collections;
import java.util.List;

public class TestDataAsRelatedFileProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    Project project = context.getData(CommonDataKeys.PROJECT);
    PsiElement element = context.getData(CommonDataKeys.PSI_ELEMENT);
    if (editor == null || element == null || project == null) return Collections.emptyList();

    PsiMethod method = UastContextKt.getUastParentOfType(element, UMethod.class);
    if (method == null) return Collections.emptyList();

    List<String> testDataFiles = NavigateToTestDataAction.findTestDataFiles(context);
    return testDataFiles == null || testDataFiles.isEmpty()
           ? Collections.emptyList()
           : Collections.singletonList(new TestDataRelatedItem(method, editor, testDataFiles));
  }
}
