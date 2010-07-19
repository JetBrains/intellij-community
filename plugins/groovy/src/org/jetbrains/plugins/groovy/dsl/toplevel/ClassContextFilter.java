package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public class ClassContextFilter implements ContextFilter {
  private final ElementPattern<PsiClass> myPattern;

  public ClassContextFilter(ElementPattern<PsiClass> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return myPattern.accepts(findPsiClass(descriptor.getProject(), descriptor.getResolveScope(), descriptor.getQualifiedName(), ctx), ctx);
  }

  @Nullable
  private static PsiClass findPsiClass(Project project, GlobalSearchScope scope, String fqName, ProcessingContext ctx) {
    final String key = getClassKey(fqName);
    final Object cached = ctx.get(key);
    if (cached == Boolean.FALSE) {
      return null;
    }
    if (cached instanceof PsiClass) {
      return (PsiClass)cached;
    }

    final PsiClass found = JavaPsiFacade.getInstance(project).findClass(fqName, scope);
    ctx.put(key, found == null ? Boolean.FALSE : found);
    return found;
  }

  public static String getClassKey(String fqName) {
    return "Class: " + fqName;
  }
}
