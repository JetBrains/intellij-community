package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult {
  private PsiElement myElement;
  private boolean myIsAccessible;

  public GroovyResolveResultImpl(PsiElement element, boolean isAccessible) {
    myElement = element;
    myIsAccessible = isAccessible;
  }

  public boolean isAccessible() {
    return myIsAccessible;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return isAccessible();
  }
}
