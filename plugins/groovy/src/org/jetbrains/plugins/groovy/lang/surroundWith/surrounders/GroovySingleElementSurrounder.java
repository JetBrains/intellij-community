package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
abstract public class GroovySingleElementSurrounder implements Surrounder {
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return elements.length == 1 &&  isApplicable(elements[0]);
  }

  protected abstract boolean isApplicable(PsiElement element);
}