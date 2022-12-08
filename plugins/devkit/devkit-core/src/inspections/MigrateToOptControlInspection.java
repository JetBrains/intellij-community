// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptComponent;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;

import java.util.List;

public class MigrateToOptControlInspection extends DevKitUastInspectionBase {
  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!method.getName().equals("createOptionsPanel")) return null;
    if (!method.getUastParameters().isEmpty()) return null;
    PsiClass psiClass = method.getJavaPsi().getContainingClass();
    if (psiClass == null || !InheritanceUtil.isInheritor(psiClass, "com.intellij.codeInspection.InspectionProfileEntry")) return null;
    UExpression body = method.getUastBody();
    if (body == null) return null;
    OptPane pane = createOptPane(body);
    if (pane == null) return null;
    UElement anchor = method.getUastAnchor();
    if (anchor == null) return null;
    PsiElement sourcePsi = anchor.getSourcePsi();
    if (sourcePsi == null) return null;
    ProblemDescriptor descriptor =
      manager.createProblemDescriptor(sourcePsi, DevKitBundle.message("inspection.migrate.to.opt.control.message"),
                                      new ConvertToOptPaneFix(pane),
                                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true);
    return new ProblemDescriptor[]{descriptor};
  }

  private static @Nullable OptPane createOptPane(UExpression body) {
    if (body instanceof UBlockExpression blockExpression) {
      List<UExpression> expressions = blockExpression.getExpressions();
      if (expressions.size() == 1) {
        body = expressions.get(0);
      }
    }
    if (body instanceof UReturnExpression returnExpression) {
      UExpression expression = returnExpression.getReturnExpression();
      if (expression instanceof UCallExpression callExpression && callExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
        PsiMethod target = callExpression.resolve();
        if (target != null && target.getName().equals("SingleCheckboxOptionsPanel")) {
          List<UExpression> arguments = callExpression.getValueArguments();
          if (arguments.size() == 3) {
            String bindId = getPropertyName(arguments.get(2));
            PsiElement messagePsi = arguments.get(0).getSourcePsi();
            if (bindId != null && messagePsi != null) {
              return OptPane.pane(OptPane.checkbox(bindId, messagePsi.getText()));
            }
          }
        }
      }
    }
    return null;
  }

  private static String getPropertyName(UExpression expression) {
    if (expression instanceof ULiteralExpression literal) {
      return ObjectUtils.tryCast(literal.getValue(), String.class);
    }
    if (expression instanceof UQualifiedReferenceExpression ref &&
        ref.getSelector() instanceof USimpleNameReferenceExpression sel &&
        sel.getIdentifier().equals("name") &&
        ref.getReceiver() instanceof UCallableReferenceExpression receiver &&
        receiver.getQualifierExpression() == null) {
      return receiver.getCallableName();
    }
    return null;
  }

  private static class ConvertToOptPaneFix implements LocalQuickFix {
    @SafeFieldForPreview
    private final OptPane myPane;

    private ConvertToOptPaneFix(@NotNull OptPane pane) { myPane = pane; }

    @Override
    public @NotNull String getFamilyName() {
      return DevKitBundle.message("inspection.migrate.to.opt.control.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      UMethod method = UastContextKt.findUElementAt(element.getContainingFile(), element.getTextOffset(), UMethod.class);
      if (method == null) return;
      PsiElement psi = method.getSourcePsi();
      if (psi == null) return;
      Language language = psi.getLanguage();
      UastCodeGenerationPlugin plugin = UastCodeGenerationPlugin.byLanguage(language);
      if (plugin == null) return;

      boolean kotlin = language != JavaLanguage.INSTANCE;
      StringBuilder builder = new StringBuilder();
      if (kotlin) {
        builder.append("override fun getOptionsPane() = ");
      } else {
        builder.append("@Override public @NotNull com.intellij.codeInspection.options.OptPane getOptionsPane() {\nreturn ");
      }
      serialize(myPane, builder);
      if (!kotlin) {
        builder.append(";\n}");
      }
      UMethod uMethod = plugin.getElementFactory(project).createMethodFromText(builder.toString(), psi);
      if (uMethod == null) return;
      PsiElement newMethod = uMethod.getSourcePsi();
      if (newMethod == null) return;
      psi.replace(newMethod);
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return super.getOptionsPane();
  }

  static void serialize(@NotNull OptPane pane, @NotNull StringBuilder builder) {
    builder.append("com.intellij.codeInspection.options.OptPane.pane(\n");
    List<@NotNull OptComponent> components = pane.components();
    for (OptComponent component : components) {
      serialize(component, builder);
      builder.append(",\n");
    }
    builder.setLength(builder.length() - (components.isEmpty() ? 1 : 2));
    builder.append(")");
  }

  static void serialize(OptComponent component, StringBuilder builder) {
    if (component instanceof OptCheckbox checkbox) {
      builder.append("com.intellij.codeInspection.options.OptPane.checkbox(");
      builder.append('"').append(StringUtil.escapeStringCharacters(checkbox.bindId())).append("\", ");
      builder.append(checkbox.label().label());
      for (OptComponent child : checkbox.children()) {
        builder.append(",\n");
        serialize(child, builder);
      }
      builder.append(")");
    }
  }
}
