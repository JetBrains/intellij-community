// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.options.*;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;

import java.util.ArrayList;
import java.util.List;

final class MigrateToOptControlInspection extends DevKitUastInspectionBase {
  private static final String OPT_PANE = "com.intellij.codeInspection.options.OptPane";

  MigrateToOptControlInspection() {
    super(UMethod.class);
  }

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, OPT_PANE);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!method.getName().equals("createOptionsPanel")) return null;
    if (!method.getUastParameters().isEmpty()) return null;
    PsiClass psiClass = method.getJavaPsi().getContainingClass();
    if (psiClass == null || !InheritanceUtil.isInheritor(psiClass, "com.intellij.codeInspection.InspectionProfileEntry")) return null;

    Language language = psiClass.getLanguage();
    // Currently only Java and Kotlin are supported
    if (!language.equals(JavaLanguage.INSTANCE) && !language.getID().equals("kotlin")) return null;
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

  @SuppressWarnings("LanguageMismatch")
  private static @Nullable OptPane createOptPane(UExpression body) {
    if (body instanceof UBlockExpression blockExpression) {
      List<UExpression> expressions = blockExpression.getExpressions();
      if (expressions.isEmpty()) return null;
      UExpression first = expressions.get(0);
      if (expressions.size() == 1) {
        body = first;
      }
      else if (first instanceof UDeclarationsExpression decl &&
               ContainerUtil.getOnlyItem(decl.getDeclarations()) instanceof UVariable var &&
               var.getUastInitializer() instanceof UCallExpression ctor &&
               (isConstructor(ctor, "MultipleCheckboxOptionsPanel") ||
                isConstructor(ctor, "InspectionOptionsPanel")) &&
               ContainerUtil.getLastItem(expressions) instanceof UReturnExpression returnExpression &&
               returnExpression.getReturnExpression() instanceof USimpleNameReferenceExpression ref &&
               var.equals(UastContextKt.toUElement(ref.resolve()))) {
        List<OptRegularComponent> components = new ArrayList<>();
        for (int i = 1; i < expressions.size() - 1; i++) {
          OptRegularComponent component = null;
          if (expressions.get(i) instanceof UQualifiedReferenceExpression qualRef &&
              qualRef.getReceiver() instanceof USimpleNameReferenceExpression callRef &&
              var.equals(UastContextKt.toUElement(callRef.resolve())) &&
              qualRef.getSelector() instanceof UCallExpression call) {
            // TODO: support addDependentCheckBox
            if (call.isMethodNameOneOf(List.of("addCheckbox", "addCheckboxEx"))) {
              List<UExpression> arguments = call.getValueArguments();
              if (arguments.size() == 2) {
                String bindId = getExpressionText(arguments.get(1));
                String messagePsi = getExpressionText(arguments.get(0));
                if (bindId != null && messagePsi != null) {
                  component = OptPane.checkbox(bindId, messagePsi);
                }
              }
            }
          }
          if (component == null) return null;
          components.add(component);
        }
        return new OptPane(components);
      }
    }
    if (body instanceof UReturnExpression returnExpression) {
      UExpression expression = returnExpression.getReturnExpression();
      if (expression instanceof UCallExpression ctor) {
        boolean checkbox = isConstructor(ctor, "SingleCheckboxOptionsPanel");
        boolean intField = isConstructor(ctor, "SingleIntegerFieldOptionsPanel");
        if (checkbox || intField) {
          List<UExpression> arguments = ctor.getValueArguments();
          if (arguments.size() == 3 || (intField && arguments.size() == 4)) {
            String bindId = getExpressionText(arguments.get(2));
            String messagePsi = getExpressionText(arguments.get(0));
            if (bindId != null && messagePsi != null) {
              return OptPane.pane(intField ?
                                  OptPane.number(bindId, messagePsi, Integer.MIN_VALUE, Integer.MAX_VALUE) :
                                  OptPane.checkbox(bindId, messagePsi));
            }
          }
        }
      }
    }
    return null;
  }

  private static @Nullable @NlsSafe String getExpressionText(UExpression expression) {
    PsiElement psi = expression.getSourcePsi();
    if (psi == null) return null;
    if (psi.getClass().getSimpleName().equals("KtLiteralStringTemplateEntry")) {
      psi = psi.getParent();
    }
    return psi.getText();
  }

  private static boolean isConstructor(UCallExpression callExpression, String className) {
    if (callExpression.getKind() != UastCallKind.CONSTRUCTOR_CALL) return false;
    PsiMethod target = callExpression.resolve();
    return target != null && target.getName().equals(className);
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
      }
      else {
        // Use short name for return type, as import will be added anyway when processing the body
        builder.append("@Override public @org.jetbrains.annotations.NotNull " + OPT_PANE + " getOptionsPane() {\nreturn ");
      }
      serialize(myPane, builder);
      if (!kotlin) {
        builder.append(";\n}");
      }
      UMethod uMethod = plugin.getElementFactory(project).createMethodFromText(builder.toString(), psi);
      if (uMethod == null) return;
      PsiElement newMethod = uMethod.getSourcePsi();
      if (newMethod == null) return;
      shortenReferences(plugin, psi.replace(newMethod));
    }

    private void shortenReferences(@NotNull UastCodeGenerationPlugin plugin, @NotNull PsiElement element) {
      var refProcessor = new PsiRecursiveElementWalkingVisitor() {
        final List<UReferenceExpression> list = new ArrayList<>();

        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          ContainerUtil.addIfNotNull(list, UastContextKt.toUElement(element, UReferenceExpression.class));
        }
      };
      element.accept(refProcessor);
      for (UReferenceExpression expression : refProcessor.list) {
        if (expression.isPsiValid()) {
          plugin.shortenReference(expression);
        }
      }

      var staticImportProcessor = new PsiRecursiveElementWalkingVisitor() {
        final List<UQualifiedReferenceExpression> list = new ArrayList<>();

        @Override
        public void visitElement(@NotNull PsiElement element) {
          super.visitElement(element);
          UQualifiedReferenceExpression expression = UastContextKt.toUElement(element, UQualifiedReferenceExpression.class);
          if (expression != null && expression.getReceiver() instanceof UReferenceExpression qualifier) {
            PsiElement target = qualifier.resolve();
            if (target instanceof PsiClass cls && OPT_PANE.equals(cls.getQualifiedName())) {
              list.add(expression);
            }
          }
        }
      };
      element.accept(staticImportProcessor);
      for (UQualifiedReferenceExpression expression : staticImportProcessor.list) {
        if (expression.isPsiValid()) {
          plugin.importMemberOnDemand(expression);
        }
      }
    }
  }

  static void serialize(@NotNull OptPane pane, @NotNull StringBuilder builder) {
    builder.append(OPT_PANE + ".pane(\n");
    var components = pane.components();
    for (OptComponent component : components) {
      serialize(component, builder);
      builder.append(",\n");
    }
    builder.setLength(builder.length() - (components.isEmpty() ? 1 : 2));
    builder.append(")");
  }

  static void serialize(OptComponent component, StringBuilder builder) {
    if (component instanceof OptCheckbox checkbox) {
      builder.append(OPT_PANE + ".checkbox(");
      builder.append(checkbox.bindId()).append(", ");
      builder.append(checkbox.label().label());
      for (OptComponent child : checkbox.children()) {
        builder.append(",\n");
        serialize(child, builder);
      }
      builder.append(")");
    }
    if (component instanceof OptNumber number) {
      builder.append(OPT_PANE + ".number(");
      builder.append(number.bindId()).append(", ");
      builder.append(number.splitLabel().label()).append(", ");
      builder.append(LongRangeSet.point(number.minValue()).getPresentationText(LongRangeSet.all())).append(", ");
      builder.append(LongRangeSet.point(number.maxValue()).getPresentationText(LongRangeSet.all()));
      builder.append(")");
    }
  }
}
