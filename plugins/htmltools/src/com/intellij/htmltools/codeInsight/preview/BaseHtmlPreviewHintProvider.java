package com.intellij.htmltools.codeInsight.preview;

import com.intellij.codeInsight.preview.PreviewHintProvider;
import com.intellij.psi.PsiFile;
import com.intellij.xml.util.HtmlUtil;

/**
 * Created by fedorkorotkov.
 */
public abstract class BaseHtmlPreviewHintProvider implements PreviewHintProvider {
  @Override
  public boolean isSupportedFile(PsiFile file) {
    return HtmlUtil.isHtmlFile(file);
  }
}
