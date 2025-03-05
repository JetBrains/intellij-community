// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

import java.util.Collections;
import java.util.List;

final class TestDataAsRelatedFileProvider extends GotoRelatedProvider {
  @Override
  public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    Editor editor = context.getData(CommonDataKeys.EDITOR);
    Project project = context.getData(CommonDataKeys.PROJECT);
    PsiElement element = context.getData(CommonDataKeys.PSI_ELEMENT);
    if (editor == null || element == null || project == null) return Collections.emptyList();

    UMethod uMethod = UastContextKt.getUastParentOfType(element, UMethod.class);
    PsiElement ctxElement = uMethod == null ? NavigateToTestDataAction.findParametrizedClass(context) : uMethod.getSourcePsi();
    if (ctxElement == null) return Collections.emptyList();

    List<TestDataFile> testDataFiles = NavigateToTestDataAction.findTestDataFiles(context, project, false);
    return testDataFiles.isEmpty()
           ? Collections.emptyList()
           : Collections.singletonList(new TestDataRelatedItem(ctxElement, editor, testDataFiles));
  }
}
