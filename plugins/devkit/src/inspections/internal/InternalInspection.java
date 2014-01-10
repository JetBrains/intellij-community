/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.PsiUtil;

public abstract class InternalInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiUtil.EMPTY_VISITOR;
  }

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                              boolean isOnTheFly,
                                              @NotNull LocalInspectionToolSession session) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiUtil.EMPTY_VISITOR;
  }

  private static boolean isAllowed(ProblemsHolder holder) {
    if (PsiUtil.isIdeaProject(holder.getProject())) {
      return true;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(holder.getFile());
    if (PluginModuleType.isPluginModuleOrDependency(module)) {
      return true;
    }

    return false;
  }

  public abstract PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);
}
