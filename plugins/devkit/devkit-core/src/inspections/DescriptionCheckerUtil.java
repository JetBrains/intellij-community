// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class DescriptionCheckerUtil {
  public static StreamEx<GlobalSearchScope> searchScopes(Module module) {
    // Try search in narrow scopes first
    return StreamEx.<Supplier<GlobalSearchScope>>of(
      () -> GlobalSearchScope.EMPTY_SCOPE,
      module::getModuleScope,
      module::getModuleWithDependenciesScope,
      () -> {
        GlobalSearchScope[] scopes = ContainerUtil.map2Array(ModuleUtilCore.getAllDependentModules(module),
                                                             GlobalSearchScope.EMPTY_ARRAY,
                                                             Module::getModuleContentWithDependenciesScope);
        return scopes.length == 0 ? GlobalSearchScope.EMPTY_SCOPE : GlobalSearchScope.union(scopes);
      },
      () -> GlobalSearchScopesCore.projectProductionScope(module.getProject())
    ).takeWhile(supplier -> !module.isDisposed())
      .map(Supplier::get)
      .pairMap((prev, next) -> next.intersectWith(GlobalSearchScope.notScope(prev)));
  }

  /**
   * Unlike getDescriptionsDirs this includes dirs in dependent modules and even project dirs ordered by
   * search scopes (first dirs in current module, then dirs in module dependencies, then dirs in
   * dependent modules, finally other project dirs).
   *
   * @param module module to search description directories for
   * @param descriptionType type of description directory to search
   * @return lazily populated stream of candidate directories
   */
  public static StreamEx<PsiDirectory> allDescriptionDirs(Module module, DescriptionType descriptionType) {
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(module.getProject());
    final PsiPackage psiPackage = javaPsiFacade.findPackage(descriptionType.getDescriptionFolder());
    if (psiPackage == null) return StreamEx.empty();
    return searchScopes(module).flatMap(scope -> StreamEx.of(psiPackage.getDirectories(scope))).distinct();
  }

  public static PsiDirectory[] getDescriptionsDirs(Module module,
                                                   DescriptionType descriptionType) {
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(module.getProject());
    final PsiPackage psiPackage = javaPsiFacade.findPackage(descriptionType.getDescriptionFolder());
    if (psiPackage != null) {
      return psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nullable
  public static String getDescriptionDirName(PsiClass aClass) {
    String descriptionDir = "";
    PsiClass each = aClass;
    while (each != null) {
      String name = each.getName();
      if (StringUtil.isEmptyOrSpaces(name)) {
        return null;
      }
      descriptionDir = name + descriptionDir;
      each = each.getContainingClass();
    }
    return descriptionDir;
  }
}
