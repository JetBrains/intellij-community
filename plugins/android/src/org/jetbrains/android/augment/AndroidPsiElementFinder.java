package org.jetbrains.android.augment;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPsiElementFinder extends PsiElementFinder {
  @Nullable
  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final Project project = scope.getProject();

    if (project == null ||
        !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return null;
    }

    final int lastDot = qualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }
    final String shortName = qualifiedName.substring(lastDot + 1);
    final String parentName = qualifiedName.substring(0, lastDot);

    if (shortName.length() == 0 || !parentName.endsWith(".R")) {
      return null;
    }
    final PsiClass rClass = JavaPsiFacade.getInstance(project).findClass(parentName, scope);

    if (rClass == null) {
      return null;
    }
    return rClass.findInnerClassByName(shortName, false);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass aClass = findClass(qualifiedName, scope);
    return aClass != null ? new PsiClass[] {aClass} : PsiClass.EMPTY_ARRAY;
  }
}
