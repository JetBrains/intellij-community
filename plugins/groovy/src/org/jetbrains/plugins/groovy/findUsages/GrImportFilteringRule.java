// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.ImportFilteringRule;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Max Medvedev
 */
public class GrImportFilteringRule extends ImportFilteringRule {
  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    if (usage instanceof PsiElementUsage) {
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      if (psiElement != null) {
        final PsiFile containingFile = psiElement.getContainingFile();
        if (containingFile instanceof GroovyFile) {
          // check whether the element is in the import list
          return PsiTreeUtil.getParentOfType(psiElement, GrImportStatement.class, true) == null;
        }
      }
    }
    return true;
  }
}
