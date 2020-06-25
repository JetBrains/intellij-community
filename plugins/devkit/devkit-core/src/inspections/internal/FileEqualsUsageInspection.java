/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Set;

public class FileEqualsUsageInspection extends DevKitUastInspectionBase {

  static final String MESSAGE =
    "Do not use File.equals/hashCode/compareTo as they don't honor case-sensitivity on macOS. " +
    "Please use FileUtil.filesEquals/fileHashCode/compareFiles instead.";

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

    holder.registerProblem(sourcePsi, MESSAGE, ProblemHighlightType.LIKE_DEPRECATED);
  }
}
