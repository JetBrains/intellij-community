// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformer;
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineTransformationUtilKt;

public class GrInlineTransformationElementRenamer extends RenamePsiElementProcessor {

  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    final Language language = element.getLanguage();
    if (!GroovyLanguage.INSTANCE.equals(language)) {
      return false;
    }
    GroovyInlineASTTransformationPerformer performer = GroovyInlineTransformationUtilKt.getHierarchicalInlineTransformationPerformer(element);
    return performer != null && performer.computeStaticReference(element) != null;
  }

  @Override
  public void renameElement(@NotNull PsiElement element,
                            @NotNull String newName,
                            UsageInfo @NotNull [] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    for (UsageInfo usage : usages) {
      RenameUtilBase.rename(usage, newName);
    }
    if (listener != null) {
      listener.elementRenamed(element);
    }
  }
}
