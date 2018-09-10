/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ui.CheckBox;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public class RefusedBequestInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public final ExternalizableStringSet annotations =
    new ExternalizableStringSet("javax.annotation.OverridingMethodsMustInvokeSuper");
  @SuppressWarnings("PublicField") public boolean ignoreEmptySuperMethods;
  @SuppressWarnings("PublicField") public boolean onlyReportWhenAnnotated = true;

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(annotations, null);
    final JCheckBox checkBox1 = new CheckBox("Only report when super method is annotated by:", this, "onlyReportWhenAnnotated");
    final CheckBox checkBox2 = new CheckBox(InspectionGadgetsBundle.message("refused.bequest.ignore.empty.super.methods.option"),
                                            this, "ignoreEmptySuperMethods");

    panel.add(checkBox1, BorderLayout.NORTH);
    panel.add(annotationsListControl, BorderLayout.CENTER);
    panel.add(checkBox2, BorderLayout.SOUTH);

    return panel;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RefusedBequestFix();
  }

  @Override
  @NotNull
  public String getID() {
    return "MethodDoesntCallSuperMethod";
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "onlyReportWhenAnnotated", "annotations");
    writeBooleanOption(node, "onlyReportWhenAnnotated", false);
    annotations.writeSettings(node, "annotations");
  }

  @Override
  public void readSettings(@NotNull Element node) {
    onlyReportWhenAnnotated = false;
    super.readSettings(node);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("refused.bequest.display.name");
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

  private static class RefusedBequestFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Insert call to super method";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      assert method != null;
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      final StringBuilder statementText = new StringBuilder();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (returnType != null && !PsiType.VOID.equals(returnType)) {
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
      if (isOnTheFly()) {
        HighlightUtils.highlightElement(element2);
        if (element2 instanceof PsiDeclarationStatement) {
          final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)element2;
          final PsiLocalVariable variable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
          HighlightUtils.showRenameTemplate(body, variable);
        }
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

    private boolean isTrivial(PsiMethod method) {
      final PsiElement element = method.getNavigationElement();
      return MethodUtils.isTrivial(element instanceof PsiMethod ? (PsiMethod)element : method, true);
    }

    private boolean isJUnitSetUpOrTearDown(PsiMethod method) {
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
      return superMethod == null || superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ? null : superMethod;
    }
  }
}
