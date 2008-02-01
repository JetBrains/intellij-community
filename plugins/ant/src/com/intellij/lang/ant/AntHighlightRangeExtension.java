package com.intellij.lang.ant;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * @author yole
 */
public class AntHighlightRangeExtension implements HighlightRangeExtension {
  public List<PsiElement> getElementsToHighlight(final PsiElement root,
                                                 final PsiElement commonParent,
                                                 final int startOffset,
                                                 final int endOffset) {
    return null;
  }

  public boolean isForceHighlightParents(final PsiFile file) {
    return CodeInsightUtil.isAntFile(file);
  }
}
