package com.intellij.lang.ant;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.impl.reference.AntPropertyReference;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 14, 2007
 */
public class AntPropertyRefSelectioner implements SelectWordUtil.Selectioner{
  public boolean canSelect(final PsiElement e) {
    return getRangeToSelect(e) != null;
  }

  public List<TextRange> select(final PsiElement e, final CharSequence editorText, final int cursorOffset, final Editor editor) {
    final TextRange textRange = getRangeToSelect(e);
    return textRange == null? Collections.<TextRange>emptyList() : Collections.singletonList(textRange);
  }
  
  @Nullable
  private static TextRange getRangeToSelect(PsiElement e) {
    final PsiFile containingFile = e.getContainingFile();
    if (containingFile == null) {
      return null;
    }
    final AntFile antFile = AntSupport.getAntFile(containingFile);
    if (antFile == null) {
      return null;
    }
    final PsiElement antElement = antFile.findElementAt(e.getTextOffset());
    if (antElement == null) {
      return null;
    }
    final TextRange antElementRange = antElement.getTextRange();
    final TextRange selectionElementRange = e.getTextRange();
    final PsiReference[] refs = antElement.getReferences();
    for (PsiReference ref : refs) {
      if (ref instanceof AntPropertyReference) {
        TextRange refRange = ref.getRangeInElement();
        refRange = refRange.shiftRight(antElementRange.getStartOffset());
        if (selectionElementRange.contains(refRange)) {
          return refRange;
        }
      }
    }
    return null;
  }
}
