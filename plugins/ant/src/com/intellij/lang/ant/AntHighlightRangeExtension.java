package com.intellij.lang.ant;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiFile;

/**
 * @author yole
 */
public class AntHighlightRangeExtension implements HighlightRangeExtension {

  public boolean isForceHighlightParents(final PsiFile file) {
    return CodeInsightUtil.isAntFile(file);
  }
}
