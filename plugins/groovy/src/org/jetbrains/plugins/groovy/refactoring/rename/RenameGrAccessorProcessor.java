// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class RenameGrAccessorProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PsiMethod &&
           !(element instanceof GrAccessorMethod) &&
           GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)element);
  }

  @Override
  public void renameElement(@NotNull PsiElement psiElement,
                            @NotNull String newName,
                            @NotNull UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {

  }

  @Override
  public void prepareRenaming(@NotNull PsiElement element, @NotNull String newName, @NotNull Map<PsiElement, String> allRenames, @NotNull SearchScope scope) {
    super.prepareRenaming(element, newName, allRenames, scope);
    final PsiField field = GroovyPropertyUtils.findFieldForAccessor((PsiMethod)element, false);
    if (field != null) {

    }
  }
}
