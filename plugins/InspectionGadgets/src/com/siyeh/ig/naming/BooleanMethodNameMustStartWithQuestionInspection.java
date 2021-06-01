/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class BooleanMethodNameMustStartWithQuestionInspection extends BaseInspection {

  public static final @NonNls String DEFAULT_QUESTION_WORDS =
    "add,are,can,check,contains,could,endsWith,equals,has,is,matches,must,put,remove,shall,should,startsWith,was,were,will,would";
  @SuppressWarnings("PublicField")
  public boolean ignoreBooleanMethods = false;
  @SuppressWarnings("PublicField")
  public boolean ignoreInAnnotationInterface = true;
  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBaseMethods = true;
  @SuppressWarnings("PublicField")
  @NonNls public String questionString = DEFAULT_QUESTION_WORDS;
  List<String> questionList = new ArrayList<>(32);

  public BooleanMethodNameMustStartWithQuestionInspection() {
    parseString(this.questionString, this.questionList);
  }

  @Override
  public JComponent createOptionsPanel() {
    final var panel = new MultipleCheckboxOptionsPanel(this);

    final ListTable table = new ListTable(new ListWrappingTableModel(questionList, InspectionGadgetsBundle
      .message("boolean.method.name.must.start.with.question.table.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table, InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.table.label"), true);
    panel.addGrowing(tablePanel);

    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option"), "ignoreBooleanMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.boolean.methods.in.an.interface.option"), "ignoreInAnnotationInterface");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), "onlyWarnOnBaseMethods");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("boolean.method.name.must.start.with.question.problem.descriptor");
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
      for (String question : questionList) {
        if (name.startsWith(question)) {
          return;
        }
      }
      if (onlyWarnOnBaseMethods) {
        if (MethodUtils.hasSuper(method)) {
          return;
        }
      }
      else if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
