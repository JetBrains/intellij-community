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

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public class UndesirableClassUsageInspection extends InternalInspection {

  private static final Map<String, String> CLASSES = ContainerUtil.<String, String>immutableMapBuilder()
    .put(JList.class.getName(), JBList.class.getName())
    .put(JTable.class.getName(), JBTable.class.getName())
    .put(JTree.class.getName(), Tree.class.getName())
    .put(JScrollPane.class.getName(), JBScrollPane.class.getName())
    .put(JTabbedPane.class.getName(), JBTabbedPane.class.getName())
    .put(JComboBox.class.getName(), ComboBox.class.getName())
    .put(QueryExecutor.class.getName(), QueryExecutorBase.class.getName())
    .put(BufferedImage.class.getName(), "UIUtil.createImage()")
    .build();

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiJavaCodeReferenceElement ref = expression.getClassReference();
        if (ref == null) return;

        PsiElement res = ref.resolve();
        if (res == null) return;

        String name = ((PsiClass)res).getQualifiedName();
        if (name == null) return;

        String replacement = CLASSES.get(name);
        if (replacement == null) return;

        holder.registerProblem(expression, "Please use '" + replacement + "' instead", ProblemHighlightType.LIKE_DEPRECATED);
      }
    };
  }
}
