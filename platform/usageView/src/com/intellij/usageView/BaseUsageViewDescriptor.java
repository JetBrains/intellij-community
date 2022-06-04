// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getProcessedElementsHeader() {
    return UsageViewBundle.message("element.or.elements.to.be.refactored");
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getCodeReferencesText(int usagesCount, int filesCount) {
    return UsageViewBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }
}