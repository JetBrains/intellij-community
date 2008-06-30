package com.intellij.lang.ant;

import com.intellij.codeInsight.hint.DeclarationRangeHandler;
import com.intellij.codeInsight.hint.DeclarationRangeUtil;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AntDeclarationRangeHandler implements DeclarationRangeHandler {
  @NotNull
  public TextRange getDeclarationRange(@NotNull final PsiElement container) {
    AntStructuredElement element = (AntStructuredElement) container;
    return DeclarationRangeUtil.getDeclarationRange(element.getSourceElement());
  }
}
