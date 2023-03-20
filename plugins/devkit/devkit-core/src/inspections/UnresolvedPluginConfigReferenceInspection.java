// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.references.PluginConfigReference;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

/**
 * Highlights all unresolved {@link PluginConfigReference}s in code.
 */
public class UnresolvedPluginConfigReferenceInspection extends LocalInspectionTool {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final Module module = ModuleUtilCore.findModuleForFile(holder.getFile());
    if (module == null || !PsiUtil.isPluginModule(module)) return PsiElementVisitor.EMPTY_VISITOR;

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);

        UInjectionHost expression = UastContextKt.toUElement(element, UInjectionHost.class);
        if (expression == null) return;

        for (PsiReference reference : element.getReferences()) {
          if (reference instanceof PluginConfigReference &&
              !reference.isSoft() &&
              reference.resolve() == null) {
            holder.registerProblem(reference);
          }
        }
      }
    };
  }
}
