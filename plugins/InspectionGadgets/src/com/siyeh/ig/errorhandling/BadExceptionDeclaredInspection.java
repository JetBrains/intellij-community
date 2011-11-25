/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class BadExceptionDeclaredInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionsString = "";

  /**
   * @noinspection PublicField
   */
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      "java.lang.Throwable",
      "java.lang.Exception",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.NullPointerException",
      "java.lang.ClassCastException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  /**
   * @noinspection PublicField
   */
  public boolean ignoreTestCases = false;

  public BadExceptionDeclaredInspection() {
    if (exceptionsString.length() != 0) {
      exceptions.clear();
      final List<String> strings =
        StringUtil.split(exceptionsString, ",");
      for (String string : strings) {
        exceptions.add(string);
      }
      exceptionsString = "";
    }
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionDeclared";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "bad.exception.declared.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "bad.exception.declared.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new BorderLayout());

    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions,
                                               InspectionGadgetsBundle.message(
                                                 "exception.class.column.name")));
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);
    UiUtils.setScrollPaneSize(scrollPane, 7, 25);
    final ActionToolbar toolbar =
      UiUtils.createAddRemoveTreeClassChooserToolbar(table,
                                                     InspectionGadgetsBundle.message(
                                                       "choose.exception.class"),
                                                     "java.lang.Throwable");

    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "bad.exception.declared.ignore.exceptions.declared.in.tests.option"),
                                           this, "ignoreTestCases");

    panel.add(toolbar.getComponent(), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);

    return panel;
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionDeclaredVisitor();
  }

  private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignoreTestCases) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null &&
            TestFrameworks.getInstance().isTestClass(containingClass)) {
          return;
        }
        if (TestUtils.isJUnitTestMethod(method)) {
          return;
        }
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] references =
        throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass)) {
          continue;
        }
        final PsiClass thrownClass = (PsiClass)element;
        final String qualifiedName = thrownClass.getQualifiedName();
        if (qualifiedName != null &&
            exceptions.contains(qualifiedName)) {
          registerError(reference);
        }
      }
    }
  }
}