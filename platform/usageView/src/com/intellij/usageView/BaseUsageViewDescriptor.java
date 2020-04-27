// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class BaseUsageViewDescriptor implements UsageViewDescriptor {

  private final PsiElement[] myElements;

  public BaseUsageViewDescriptor(PsiElement... elements) {
    myElements = elements;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return "Element(s) to be refactored:";
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}
