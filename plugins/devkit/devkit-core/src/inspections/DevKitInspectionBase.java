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

  static boolean isAllowed(@NotNull ProblemsHolder holder) {
    if (PsiUtil.isIdeaProject(holder.getProject())) {
      return true;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
    if (module == null) {
      return false;
    }

    if (PluginModuleType.isPluginModuleOrDependency(module)) {
      return true;
    }

    if (PsiUtil.isPluginModule(module)) {
      return true;
    }

    // always run in tests
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }
}
