package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

/**
 * @author peter
 */
public class ClassContextFilter implements ContextFilter {
  private final ElementPattern<Pair<PsiType, PsiElement>> myPattern;

  public ClassContextFilter(ElementPattern<Pair<PsiType, PsiElement>> pattern) {
    myPattern = pattern;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    final PsiFile place = descriptor.getPlaceFile();
    return myPattern.accepts(new Pair<PsiType, PsiElement>(findPsiType(descriptor.getProject(), descriptor.getTypeText(), place, ctx),
                                                           place), ctx);
  }

  @Nullable
  private static PsiType findPsiType(Project project, String typeText, PsiFile place, ProcessingContext ctx) {
    final String key = getClassKey(typeText);
    final Object cached = ctx.get(key);
    if (cached instanceof PsiType) {
      return (PsiType)cached;
    }

    final PsiType found = JavaPsiFacade.getElementFactory(project).createTypeFromText(typeText, place);
    ctx.put(key, found);
    return found;
  }

  public static String getClassKey(String fqName) {
    return "Class: " + fqName;
  }
}
