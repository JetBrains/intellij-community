// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.QueryExecutor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@VisibleForTesting
@ApiStatus.Internal
public final class UndesirableClassUsageInspection extends DevKitUastInspectionBase {

  private static final @NonNls Map<String, String> CLASSES = Map.of(
    JList.class.getName(), JBList.class.getName(),
    JTable.class.getName(), JBTable.class.getName(),
    JTree.class.getName(), Tree.class.getName(),
    JScrollPane.class.getName(), JBScrollPane.class.getName(),
    JTabbedPane.class.getName(), JBTabbedPane.class.getName(),
    JComboBox.class.getName(), ComboBox.class.getName(),
    QueryExecutor.class.getName(), QueryExecutorBase.class.getName(),
    BufferedImage.class.getName(), "UIUtil.createImage()");

  public UndesirableClassUsageInspection() {
    super(UField.class, UMethod.class);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkBody(method, manager, isOnTheFly);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkField(@NotNull UField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return checkBody(field, manager, isOnTheFly);
  }

  private static ProblemDescriptor @Nullable [] checkBody(@NotNull UElement uElement,
                                                          @NotNull InspectionManager manager,
                                                          boolean isOnTheFly) {
    List<ProblemDescriptor> descriptors = new SmartList<>();
    uElement.accept(new AbstractUastVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        if (expression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          final PsiClass psiClass = PsiTypesUtil.getPsiClass(expression.getReturnType());
          if (psiClass != null) {
            final String name = psiClass.getQualifiedName();
            String replacement = name==null?null:CLASSES.get(name);
            if (replacement != null) {
              descriptors.add(
                manager.createProblemDescriptor(Objects.requireNonNull(expression.getSourcePsi()),
                                                DevKitBundle.message("inspections.undesirable.class.use.instead", replacement),
                                                true,
                                                ProblemHighlightType.LIKE_DEPRECATED, isOnTheFly));
            }
          }
        }
        return true;
      }
    });

    return descriptors.isEmpty() ? null : descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }
}