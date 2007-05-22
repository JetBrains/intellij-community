package org.jetbrains.plugins.groovy.lang.surroundWith.descriptors;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class GroovyBlockSurroundDescriptor implements SurroundDescriptor {
  @NotNull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    return new PsiElement[0];
  }

  @NotNull
  public Surrounder[] getSurrounders() {
    return new Surrounder[0];
  }
}
