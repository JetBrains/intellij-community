/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class BooleanMethodNameMustStartWithQuestionInspection extends NonBooleanMethodNameMayNotStartWithQuestionInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInAnnotationInterface = true;

  public BooleanMethodNameMustStartWithQuestionInspection() {}

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    // keep original order of settings written for compatibility
    writeOption(element, "ignoreBooleanMethods");
    writeOption(element, "ignoreInAnnotationInterface");
    writeOption(element, "onlyWarnOnBaseMethods");
    questionString = formatString(questionList);
    writeOption(element, "questionString");
  }

  @Override
  public @NotNull MultipleCheckboxOptionsPanel createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = super.createOptionsPanel();
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.boolean.methods.in.an.interface.option"), "ignoreInAnnotationInterface");
    return panel;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanMethodNameMustStartWithQuestionVisitor();
  }

  private class BooleanMethodNameMustStartWithQuestionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiType returnType = method.getReturnType();
      if (returnType == null) {
        return;
      }
      else if (!returnType.equals(PsiType.BOOLEAN)) {
        if (ignoreBooleanMethods || !returnType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
          return;
        }
      }
      if (ignoreInAnnotationInterface) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isAnnotationType()) {
          return;
        }
      }
      final String name = method.getName();
      if (startsWithQuestionWord(name) || isSpecialCase(name)) {
        return;
      }
      if (onlyWarnOnBaseMethods) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      }
      else if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method, method);
    }

    private boolean isSpecialCase(String name) {
      return name.equals("add") || name.equals("remove") || name.equals("put");
    }
  }
}
