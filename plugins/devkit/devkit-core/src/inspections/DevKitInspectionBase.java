// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.function.Predicate;

/**
 * @deprecated use {@link DevKitUastInspectionBase} instead.
 *
 * @author swr
 */
@Deprecated(forRemoval = true)
public abstract class DevKitInspectionBase extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder.getFile()) ? buildInternalVisitor(holder, isOnTheFly) : PsiElementVisitor.EMPTY_VISITOR;
  }

  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }

  public static boolean isAllowed(@NotNull PsiFile file) {
    return isAllowed(file, __ -> true);
  }

  static boolean isAllowedInPluginsOnly(@NotNull PsiFile file) {
    return isAllowed(file, DevKitInspectionBase::isPluginFile);
  }

  private static boolean isAllowed(@NotNull PsiFile file, @NotNull Predicate<? super PsiFile> predicate) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;  /* always run in tests */
    if (PsiUtil.isIdeaProject(file.getProject())) {
      return predicate.test(file);
    }
    else {
      return isInPluginModule(file);
    }
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
    boolean isPlatform = path.contains("/platform/") && !path.contains("/platform/cwm-") && !path.contains("/platform/rd-");

    // Rider lives in other repository, but also kind-of platform for Rider IDE
    boolean isRider = path.contains("/Rider/Frontend/rider/") ||
                      path.contains("/Rider/Frontend/rider-cpp-core/") ||
                      path.contains("/Rider/Frontend/rdclient-dotnet/");

    return !isPlatform && !isRider;
  }
}
