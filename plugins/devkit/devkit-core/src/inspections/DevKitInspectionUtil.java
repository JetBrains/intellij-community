// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.function.Predicate;

public final class DevKitInspectionUtil {

  static boolean isAllowedInPluginsOnly(@NotNull PsiFile file) {
    return isAllowed(file, DevKitInspectionUtil::isPluginFile);
  }

  public static boolean isAllowed(@NotNull PsiFile file) {
    return isAllowed(file, __ -> true);
  }

  public static boolean isClassAvailable(@NotNull ProblemsHolder holder, @NonNls String classFqn) {
    return JavaPsiFacade.getInstance(holder.getProject()).findClass(classFqn, holder.getFile().getResolveScope()) != null;
  }

  private static boolean isAllowed(@NotNull PsiFile file, @NotNull Predicate<? super PsiFile> predicate) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;  // always run in tests

    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;
    if (TestSourcesFilter.isTestSources(vFile, file.getProject())) return false;

    if (PsiUtil.isIdeaProject(file.getProject())) {
      return predicate.test(file);
    }

    return isInPluginModule(file);
  }

  private static boolean isInPluginModule(@NotNull PsiFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) {
      return false;
    }

    return PluginModuleType.isPluginModuleOrDependency(module) ||
           PsiUtil.isPluginModule(module);
  }

  // TODO expand this check
  private static boolean isPluginFile(@NotNull PsiFile file) {
    String path = file.getVirtualFile().getPath();
    boolean isPlatform = path.contains("/platform/") &&
                         !path.contains("/remote-dev/cwm-") &&
                         !path.contains("/remote-dev/rd-");

    // Rider lives in other repository, but also kind-of platform for Rider IDE
    boolean isRider = path.contains("/rider/") ||
                      path.contains("/rider-cpp-core/") ||
                      path.contains("/rdclient-dotnet/");

    return !isPlatform && !isRider;
  }
}
