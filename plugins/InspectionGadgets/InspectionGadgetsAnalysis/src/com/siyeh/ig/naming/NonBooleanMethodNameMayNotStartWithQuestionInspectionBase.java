/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NonBooleanMethodNameMayNotStartWithQuestionInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  @NonNls public String questionString = BooleanMethodNameMustStartWithQuestionInspectionBase.DEFAULT_QUESTION_WORDS;
  @SuppressWarnings("PublicField")
  public boolean ignoreBooleanMethods = false;
  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBaseMethods = true;

  List<String> questionList = new ArrayList(32);

  public NonBooleanMethodNameMayNotStartWithQuestionInspectionBase() {
    parseString(questionString, questionList);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.boolean.method.name.must.not.start.with.question.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.boolean.method.name.must.not.start.with.question.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(questionString, questionList);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    questionString = formatString(questionList);
    super.writeSettings(element);
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonBooleanMethodNameMayNotStartWithQuestionVisitor();
  }

  private class NonBooleanMethodNameMayNotStartWithQuestionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiType returnType = method.getReturnType();
      if (returnType == null || returnType.equals(PsiType.BOOLEAN)) {
        return;
      }
      if (ignoreBooleanMethods && returnType.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
        return;
      }
      final String name = method.getName();
      boolean startsWithQuestionWord = false;
      for (String question : questionList) {
        if (name.startsWith(question)) {
          if (name.length() == question.length()) {
            startsWithQuestionWord = true;
            break;
          }
          final char nextChar = name.charAt(question.length());
          if (Character.isUpperCase(nextChar) || nextChar == '_') {
            startsWithQuestionWord = true;
            break;
          }
        }
      }
      if (!startsWithQuestionWord) {
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
  }
}
