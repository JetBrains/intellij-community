// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.commander;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.impl.SelectInTargetPsiWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;

public final class CommanderSelectInTarget extends SelectInTargetPsiWrapper {
  public CommanderSelectInTarget(final Project project) {
    super(project);
  }

  public String toString() {
    return IdeBundle.message("select.in.commander");
  }

  @Override
  protected boolean canSelect(final PsiFileSystemItem file) {
    return file.getManager().isInProject(file);
  }

  @Override
  protected void select(PsiElement element, boolean requestFocus) {
    while (true) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        break;
      }
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) {
        break;
      }
      element = element.getParent();
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement _element = element.getOriginalElement();

    selectElementInCommander(
      () -> Commander.getInstance(myProject).selectElementInLeftPanel(_element, PsiUtilBase.getVirtualFile(_element)), requestFocus);
  }

  private void selectElementInCommander(final Runnable runnable, final boolean requestFocus) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.COMMANDER).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  @Override
  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectElementInCommander(() -> {
      final Commander commander = Commander.getInstance(myProject);
      commander.selectElementInLeftPanel(selector, virtualFile);
    }, requestFocus);

  }

  @Override
  public String getToolWindowId() {
    return ToolWindowId.COMMANDER;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.COMMANDER_WEIGHT;
  }

}
