// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class RenameGroovyPropertyProcessor extends RenamePsiElementProcessor {
  Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.rename.RenameGroovyPropertyProcessor");

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames) {
    LOG.assertTrue(element instanceof PropertyForRename);
    final List<? extends PsiElement> elementsToRename = ((PropertyForRename)element).getElementsToRename();
    for (PsiElement psiElement : elementsToRename) {
      if (psiElement instanceof GrField) {
        allRenames.put(psiElement, newName);
      }
      else if (psiElement instanceof GrMethod) {
        if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)psiElement)) {
          allRenames.put(psiElement, RenamePropertyUtil.getGetterNameByOldName(newName, ((PsiMethod)psiElement).getName()));
        }
        else {
          allRenames.put(psiElement, GroovyPropertyUtils.getSetterName(newName));
        }
      }
    }
    allRenames.remove(element);
  }

  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull String newName, @NotNull UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {

    //do nothing
  }

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PropertyForRename;
  }
}
