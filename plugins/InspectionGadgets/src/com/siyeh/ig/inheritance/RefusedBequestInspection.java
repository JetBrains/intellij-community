// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.AstLoadingFilter;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * @author Bas Leijdekkers
 */
public class RefusedBequestInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public final ExternalizableStringSet annotations =
    new ExternalizableStringSet("javax.annotation.OverridingMethodsMustInvokeSuper",
                                "org.jetbrains.annotations.MustBeInvokedByOverriders");
  @SuppressWarnings("PublicField") public boolean ignoreEmptySuperMethods;
  @SuppressWarnings("PublicField") public boolean ignoreDefaultSuperMethods;
  @SuppressWarnings("PublicField") public boolean onlyReportWhenAnnotated = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyReportWhenAnnotated", InspectionGadgetsBundle.message("inspection.refused.bequest.super.annotated.option"),
               stringList("annotations", "", new JavaClassValidator().annotationsOnly())),
      checkbox("ignoreEmptySuperMethods", InspectionGadgetsBundle.message("refused.bequest.ignore.empty.super.methods.option")),
      checkbox("ignoreDefaultSuperMethods", InspectionGadgetsBundle.message("refused.bequest.ignore.default.super.methods.option"))
    );
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new RefusedBequestFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "MethodDoesntCallSuperMethod";
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "onlyReportWhenAnnotated", "annotations", "ignoreDefaultSuperMethods");
    writeBooleanOption(node, "onlyReportWhenAnnotated", false);
    writeBooleanOption(node, "ignoreDefaultSuperMethods", false);
    annotations.writeSettings(node, "annotations");
  }

  @Override
  public void readSettings(@NotNull Element node) {
    onlyReportWhenAnnotated = false; // should be false when not present, used to be false by default in the past.
    super.readSettings(node);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("refused.bequest.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RefusedBequestVisitor();
  }

  private static class RefusedBequestFix extends PsiUpdateModCommandQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("refused.bequest.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement methodName, @NotNull ModPsiUpdater updater) {
      final PsiMethod method = (PsiMethod)methodName.getParent();
      assert method != null;
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      final @NonNls StringBuilder statementText = new StringBuilder();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (returnType != null && !PsiTypes.voidType().equals(returnType)) {
        if (JavaCodeStyleSettings.getInstance(method.getContainingFile()).GENERATE_FINAL_LOCALS) {
          statementText.append("final ");
        }
        statementText.append(returnType.getCanonicalText()).append(' ');
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final SuggestedNameInfo baseNameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, returnType);
        final SuggestedNameInfo nameInfo = codeStyleManager.suggestUniqueVariableName(baseNameInfo, body, true);
        statementText.append(nameInfo.names.length > 0 ? nameInfo.names[0] : "result");
        statementText.append('=');
        final MethodSignatureBackedByPsiMethod superMethodSignature = MethodUtils.getSuperMethodSignature(method);
        if (superMethodSignature == null) {
          return;
        }
        final PsiMethod superMethod = superMethodSignature.getMethod();
        final PsiType superReturnType = superMethod.getReturnType();
        final PsiType substitutedType = superMethodSignature.getSubstitutor().substitute(superReturnType);
        if (superReturnType != null && !returnType.isAssignableFrom(substitutedType)) {
          statementText.append('(').append(returnType.getCanonicalText()).append(')');
        }
      }
      statementText.append("super.").append(methodName.getText()).append('(');
      boolean comma = false;
      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        if (comma) statementText.append(',');
        else comma = true;
        statementText.append(parameter.getName());
      }
      statementText.append(");");
      final PsiStatement newStatement = factory.createStatementFromText(statementText.toString(), null);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      final PsiJavaToken brace = body.getLBrace();
      final PsiElement element = body.addAfter(newStatement, brace);
      final PsiElement element1 = styleManager.reformat(element);
      final PsiElement element2 = JavaCodeStyleManager.getInstance(project).shortenClassReferences(element1);
      updater.highlight(element2);
      if (element2 instanceof PsiDeclarationStatement declarationStatement) {
        final PsiLocalVariable variable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
        List<String> nameSuggestions = new VariableNameGenerator(variable, VariableKind.LOCAL_VARIABLE)
          .byExpression(variable.getInitializer())
          .byType(variable.getType())
          .byName("original", "superResult")
          .generateAll(true);
        updater.rename(variable, nameSuggestions);
      }
    }
  }

  private class RefusedBequestVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiMethod superMethod = getDirectSuperMethod(method);
      if (superMethod == null) {
        return;
      }
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClassName)) {
            return;
          }
        }
      }
      if (ignoreEmptySuperMethods && isTrivial(superMethod)) {
        return;
      }
      final boolean isClone = CloneUtils.isClone(method);
      if (onlyReportWhenAnnotated && !AnnotationUtil.isAnnotated(superMethod, annotations, 0)) {
        if (!isClone && !isJUnitSetUpOrTearDown(method) && !MethodUtils.isFinalize(method) || isTrivial(superMethod)) {
          return;
        }
      }
      if (isClone && ClassUtils.isSingleton(method.getContainingClass())) {
        return;
      }
      if (MethodCallUtils.containsSuperMethodCall(method) || ControlFlowUtils.methodAlwaysThrowsException(method)) {
        return;
      }
      registerMethodError(method);
    }

    private static boolean isTrivial(PsiMethod method) {
      final PsiElement element = method.getNavigationElement();
      return AstLoadingFilter.forceAllowTreeLoading(method.getContainingFile(),
                                                    () -> MethodUtils.isTrivial(element instanceof PsiMethod
                                                                                ? (PsiMethod)element
                                                                                : method, s -> s instanceof PsiThrowStatement));
    }

    private static boolean isJUnitSetUpOrTearDown(PsiMethod method) {
      final String name = method.getName();
      if (!"setUp".equals(name) && !"tearDown".equals(name)) {
        return false;
      }
      if (!method.getParameterList().isEmpty()) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(aClass, "junit.framework.TestCase");
    }

    @Nullable
    private PsiMethod getDirectSuperMethod(PsiMethod method) {
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      if (superMethod == null ||
          superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ||
          ignoreDefaultSuperMethods && superMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        return null;
      }
      return superMethod;
    }
  }
}
