package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.ResolveResult;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public interface GroovyResolveResult extends ResolveResult {
  public static final GroovyResolveResult[] EMPTY_ARRAY = new GroovyResolveResult[0];

  boolean isAccessible();

  public static final GroovyResolveResult EMPTY_RESULT = new GroovyResolveResult() {
    public boolean isAccessible() {
      return false;
    }

    @Nullable
    public PsiElement getElement() {
      return null;
    }

    public boolean isValidResult() {
      return false;
    }
  };
}
