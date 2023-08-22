// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemHolderUtilKt;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.List;

public class FileEqualsUsageInspection extends DevKitUastInspectionBase {

  private static final String[] METHOD_NAMES = new String[]{"equals", "compareTo", "hashCode"};

  @SuppressWarnings("unchecked")
  private static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class};

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        inspectCallExpression(node, holder);

        return true;
      }
    }, HINTS);
  }

  private static void inspectCallExpression(@NotNull UCallExpression node, @NotNull ProblemsHolder holder) {
    if (!node.isMethodNameOneOf(List.of(METHOD_NAMES))) return;
    final PsiMethod psiMethod = node.resolve();
    if (psiMethod == null) return;
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return;
    if (!CommonClassNames.JAVA_IO_FILE.equals(containingClass.getQualifiedName())) return;

    if (!DevKitInspectionUtil.isClassAvailable(holder, FileUtil.class.getName())) return;

    ProblemHolderUtilKt.registerUProblem(holder, node, DevKitBundle.message("inspections.file.equals.method"),
                                         LocalQuickFix.notNullElements(), ProblemHighlightType.LIKE_DEPRECATED);
  }
}
