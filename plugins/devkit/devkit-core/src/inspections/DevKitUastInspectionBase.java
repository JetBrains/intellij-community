// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

/**
 * @author yole
 */
public abstract class DevKitUastInspectionBase extends AbstractBaseUastLocalInspectionTool {

  protected DevKitUastInspectionBase() {
  }

  protected DevKitUastInspectionBase(Class<? extends UElement>... uElementsTypesHint) {
    super(uElementsTypesHint);
  }

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiElementVisitor.EMPTY_VISITOR;
  }

  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return DevKitInspectionBase.isAllowed(holder);
  }

  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }
}
