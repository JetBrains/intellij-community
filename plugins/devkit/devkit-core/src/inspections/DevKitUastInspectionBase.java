// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UIdentifier;

public abstract class DevKitUastInspectionBase extends AbstractBaseUastLocalInspectionTool {

  protected DevKitUastInspectionBase() {
  }

  /**
   * When *NOT* overriding {@link #buildInternalVisitor} but using {@code checkClass|Method|Field}.
   */
  @SafeVarargs
  protected DevKitUastInspectionBase(Class<? extends UElement>... uElementsTypesHint) {
    super(uElementsTypesHint);
  }

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder) ? buildInternalVisitor(holder, isOnTheFly) : PsiElementVisitor.EMPTY_VISITOR;
  }

  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return DevKitInspectionUtil.isAllowed(holder.getFile());
  }

  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return super.buildVisitor(holder, isOnTheFly);
  }

  protected static @NotNull ProblemsHolder createProblemsHolder(@NotNull UElement uElement,
                                                                @NotNull InspectionManager manager,
                                                                boolean isOnTheFly) {
    PsiElement sourcePsi = uElement.getSourcePsi();
    if (sourcePsi != null) {
      return new ProblemsHolder(manager, sourcePsi.getContainingFile(), isOnTheFly);
    }
    throw new IllegalStateException("Could not create problems holder");
  }

  protected static boolean hasMethodIdentifierEqualTo(@NotNull UCallExpression expression, String @NotNull ... methodNames) {
    UIdentifier identifier = expression.getMethodIdentifier();
    if (identifier == null) return false;
    String methodName = identifier.getName();
    return ArrayUtil.contains(methodName, methodNames);
  }
}
