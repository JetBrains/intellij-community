/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.commander;

import com.intellij.ide.SelectInManager;
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
    return SelectInManager.COMMANDER;
  }

  protected boolean canSelect(final PsiFileSystemItem file) {
    return file.getManager().isInProject(file);
  }

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

  protected void select(final Object selector, final VirtualFile virtualFile, final boolean requestFocus) {
    selectElementInCommander(() -> {
      final Commander commander = Commander.getInstance(myProject);
      commander.selectElementInLeftPanel(selector, virtualFile);
    }, requestFocus);

  }

  public String getToolWindowId() {
    return ToolWindowId.COMMANDER;
  }

  public String getMinorViewId() {
    return null;
  }

  public float getWeight() {
    return StandardTargetWeights.COMMANDER_WEIGHT;
  }

}
