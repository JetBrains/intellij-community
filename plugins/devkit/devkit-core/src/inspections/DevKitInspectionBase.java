// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.function.Predicate;

/**
 * Consider using {@link DevKitUastInspectionBase} instead.
 *
 * @author swr
 */
public abstract class DevKitInspectionBase extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiElementVisitor.EMPTY_VISITOR;
  }

  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }

  static boolean isAllowed(@NotNull ProblemsHolder holder) {
    return isAllowed(holder, h -> true);
  }

  static boolean isAllowedInPluginsOnly(@NotNull ProblemsHolder holder) {
    return isAllowed(holder, DevKitInspectionBase::isPluginFile);
  }

  static boolean isAllowed(@NotNull ProblemsHolder holder,
                           @NotNull Predicate<? super ProblemsHolder> predicate) {
    return ApplicationManager.getApplication().isUnitTestMode() /* always run in tests */ ||
           (PsiUtil.isIdeaProject(holder.getProject()) && predicate.test(holder)) ||
           isInPluginModule(holder);
  }

  private static boolean isInPluginModule(@NotNull ProblemsHolder holder) {
    Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
    if (module == null) {
      return false;
    }

    return PluginModuleType.isPluginModuleOrDependency(module) ||
           PsiUtil.isPluginModule(module);
  }

  // TODO expand this check
  private static boolean isPluginFile(@NotNull ProblemsHolder holder) {
    return !holder
      .getFile()
      .getVirtualFile()
      .getPath()
      .contains("/platform/");
  }
}
