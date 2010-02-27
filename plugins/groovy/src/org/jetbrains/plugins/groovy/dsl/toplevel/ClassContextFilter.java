package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class ClassContextFilter implements ContextFilter {
  private final ElementPattern<PsiClass> myPattern;

  public ClassContextFilter(ElementPattern<PsiClass> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(PsiElement place, String fqName, ProcessingContext ctx) {
    return myPattern.accepts(findPsiClass(place, fqName, ctx));
  }

  @Nullable
  private static PsiClass findPsiClass(PsiElement place, String fqName, ProcessingContext ctx) {
    final String key = getClassKey(fqName);
    final Object cached = ctx.get(key);
    if (cached == Boolean.FALSE) {
      return null;
    }
    if (cached instanceof PsiClass) {
      return (PsiClass)cached;
    }

    final PsiClass found = JavaPsiFacade.getInstance(place.getProject()).findClass(fqName, place.getResolveScope());
    ctx.put(key, found == null ? Boolean.FALSE : found);
    return found;
  }

  public static String getClassKey(String fqName) {
    return "Class: " + fqName;
  }
}
