// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Set;

public class FileEqualsUsageInspection extends DevKitUastInspectionBase {

  private static final Set<String> METHOD_NAMES = ContainerUtil.immutableSet("equals", "compareTo", "hashCode");

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        inspectCallExpression(node, holder);

        return true;
      }
    }, new Class[]{UCallExpression.class});
  }

  private static void inspectCallExpression(@NotNull UCallExpression node, @NotNull ProblemsHolder holder) {
    if (node.getKind() != UastCallKind.METHOD_CALL) return;

    final PsiMethod psiMethod = node.resolve();
    if (psiMethod == null) return;
    final PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) return;
    if (!CommonClassNames.JAVA_IO_FILE.equals(containingClass.getQualifiedName())) return;

    if (!METHOD_NAMES.contains(node.getMethodName())) return;

    if (JavaPsiFacade.getInstance(holder.getProject()).findClass(FileUtil.class.getName(), holder.getFile().getResolveScope()) == null) {
      return;
    }

    final UIdentifier identifier = node.getMethodIdentifier();
    if (identifier == null) return;
    final PsiElement sourcePsi = identifier.getSourcePsi();
    if (sourcePsi == null) return;

    holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.file.equals.method"), ProblemHighlightType.LIKE_DEPRECATED);
  }
}
