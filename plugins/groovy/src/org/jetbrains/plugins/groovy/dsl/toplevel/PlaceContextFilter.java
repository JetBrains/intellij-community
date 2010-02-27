package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public class PlaceContextFilter implements ContextFilter {
  private final ElementPattern<PsiElement> myPattern;

  public PlaceContextFilter(ElementPattern<PsiElement> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(PsiElement place, String fqName, ProcessingContext ctx) {
    return myPattern.accepts(place, ctx);
  }

}