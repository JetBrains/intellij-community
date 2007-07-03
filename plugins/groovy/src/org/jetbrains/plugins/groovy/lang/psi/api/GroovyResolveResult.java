package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.ResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author ven
 */
public interface GroovyResolveResult extends ResolveResult {
  public static final GroovyResolveResult[] EMPTY_ARRAY = new GroovyResolveResult[0];

  boolean isAccessible();

  @Nullable
  GrImportStatement getImportStatementContext();

  PsiSubstitutor getSubstitutor();

  public static final GroovyResolveResult EMPTY_RESULT = new GroovyResolveResult() {
    public boolean isAccessible() {
      return false;
    }

    public GrImportStatement getImportStatementContext() {
      return null;
    }

    public PsiSubstitutor getSubstitutor() {
      return PsiSubstitutor.EMPTY;
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
