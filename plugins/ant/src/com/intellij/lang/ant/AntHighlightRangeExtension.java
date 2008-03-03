package com.intellij.lang.ant;

import com.intellij.codeInsight.daemon.impl.HighlightRangeExtension;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.XmlUtil;

/**
 * @author yole
 */
public class AntHighlightRangeExtension implements HighlightRangeExtension {

  public boolean isForceHighlightParents(final PsiFile file) {
    return XmlUtil.isAntFile(file);
  }
}
