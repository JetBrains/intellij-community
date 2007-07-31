package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.TextRange;

/**
 * @author ven
 */
public class JavaIdentifier extends LightIdentifier {
  private PsiFile myFile;
  private TextRange myRange;

  public JavaIdentifier(PsiManager manager, PsiFile file, TextRange range) {
    super(manager, file.getText().substring(range.getStartOffset(), range.getEndOffset()));
    myFile = file;
    myRange = range;
  }

  public TextRange getTextRange() {
    return myRange;
  }

  public PsiFile getContainingFile() {
    return myFile;
  }
}
