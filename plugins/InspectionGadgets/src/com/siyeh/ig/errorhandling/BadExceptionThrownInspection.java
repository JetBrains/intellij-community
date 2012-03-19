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

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class BadExceptionThrownInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionsString = "";

  @SuppressWarnings("PublicField")
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

  public BadExceptionThrownInspection() {
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
    return "ProhibitedExceptionThrown";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "bad.exception.thrown.display.name");
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsBundle.message( "exception.class.column.name")));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.exception.class"), "java.lang.Throwable");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String exceptionName = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "bad.exception.thrown.problem.descriptor", exceptionName);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionThrownVisitor();
  }

  private class BadExceptionThrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type == null) {
        return;
      }
      final String text = type.getCanonicalText();
      if (exceptions.contains(text)) {
        registerStatementError(statement, type);
      }
    }
  }
}