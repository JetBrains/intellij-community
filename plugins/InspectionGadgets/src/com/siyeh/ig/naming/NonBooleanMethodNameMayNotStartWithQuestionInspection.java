/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.FormBuilder;
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
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  @NonNls public String questionString = "is,can,has,should,could,will,shall,check,contains,equals,startsWith,endsWith";

  @SuppressWarnings({"PublicField"})
  public boolean ignoreBooleanMethods = false;

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnBaseMethods = true;

  List<String> questionList = new ArrayList(32);

  public NonBooleanMethodNameMayNotStartWithQuestionInspection() {
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
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(questionList, InspectionGadgetsBundle
      .message("boolean.method.name.must.start.with.question.table.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table);

    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option"), this, "ignoreBooleanMethods");
    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), this, "onlyWarnOnBaseMethods");

    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(FormBuilder.createFormBuilder().addComponent(checkBox1).addComponent(checkBox2).getPanel(), BorderLayout.SOUTH);
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
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
      registerMethodError(method);
    }
  }
}
