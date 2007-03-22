/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.refactoring;

import com.intellij.lang.ant.PsiAntElement;
import com.intellij.lang.ant.psi.impl.AntNameElementImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.PsiElementRenameHandler;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 19, 2007
 */
public final class AntRenameHandler extends PsiElementRenameHandler {
  
  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    return element instanceof PsiAntElement && ((PsiAntElement)element).canRename();
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    invoke(project, BaseRefactoringAction.getPsiElementArray(dataContext), dataContext);
  }

  public void invoke(final Project project, final PsiElement[] elements, final DataContext dataContext) {
    for (int idx = 0; idx < elements.length; idx++) {
      PsiElement element = elements[idx];
      if (element instanceof AntNameElementImpl) {
        elements[idx] = ((AntNameElementImpl)element).getElementToRename();
      }
    }
    super.invoke(project, elements, dataContext);
  }
}
