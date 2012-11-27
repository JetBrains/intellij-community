package org.jetbrains.android.compiler;

import com.intellij.compiler.DependencyProcessor;
import com.intellij.compiler.make.*;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDependencyProcessor implements DependencyProcessor {
  @Override
  public void processDependencies(final CompileContext context, int classQualifiedName, final CachingSearcher searcher)
    throws CacheCorruptedException {
    if (!(context instanceof CompileContextEx)) {
      return;
    }
    final Project project = context.getProject();

    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }
    final DependencyCache depCache = ((CompileContextEx)context).getDependencyCache();
    final Cache cache = depCache.getCache();
    final String path = cache.getPath(classQualifiedName);
    final String classFileName = new File(path).getName();

    if (!AndroidCommonUtils.R_PATTERN.matcher(classFileName).matches()) {
      return;
    }
    final String qName = depCache.resolve(classQualifiedName);
    final int idx = qName.indexOf('$');
    final String topLevelClassName = idx < 0 ? qName : qName.substring(0, idx);
    final Set<String> qNamesToMark = new HashSet<String>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiClass[] classes =
          JavaPsiFacade.getInstance(project).findClasses(topLevelClassName, GlobalSearchScope.projectScope(project));

        for (PsiClass aClass : classes) {
          final Collection<PsiReference> references = searcher.findReferences(aClass, true);

          for (PsiReference reference : references) {
            final PsiClass ownerClass = getOwnerClass(reference.getElement());
            if (ownerClass != null && !ownerClass.equals(aClass)) {
              final String ownerClassQName = ownerClass.getQualifiedName();

              if (ownerClassQName != null) {
                qNamesToMark.add(ownerClassQName);
              }
            }
          }
        }
      }
    });

    for (String toMark : qNamesToMark) {
      final int qualifiedName = depCache.getSymbolTable().getId(toMark);
      depCache.markClass(qualifiedName);
    }
  }

  @Nullable
  private static PsiClass getOwnerClass(PsiElement element) {
    while (!(element instanceof PsiFile) && element != null) {
      if (element instanceof PsiClass && element.getParent() instanceof PsiJavaFile) {
        return (PsiClass)element;
      }
      element = element.getParent();
    }
    return null;
  }
}
