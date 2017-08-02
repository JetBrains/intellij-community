/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
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
public abstract class DevKitInspectionBase extends BaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiElementVisitor.EMPTY_VISITOR;
  }

  protected boolean isAllowed(ProblemsHolder holder) {
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
