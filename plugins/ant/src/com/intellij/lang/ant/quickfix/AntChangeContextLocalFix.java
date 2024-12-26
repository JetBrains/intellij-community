// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.impl.HectorComponent;
import com.intellij.codeInsight.daemon.impl.HectorComponentFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class AntChangeContextLocalFix implements LocalQuickFix {

  @Override
  public @NotNull String getName() {
    return AntBundle.message("intention.configure.highlighting.text");
  }

  @Override
  public final @NotNull String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      return;
    }
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) {
      return;
    }
    final HectorComponent component = project.getService(HectorComponentFactory.class).create(containingFile.getOriginalFile());
    component.showComponent(JBPopupFactory.getInstance().guessBestPopupLocation(editor));
  }
}
