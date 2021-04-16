// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
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
  @SuppressWarnings("PublicField")
  public String exceptionsString = "";

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      CommonClassNames.JAVA_LANG_THROWABLE,
      "java.lang.Exception",
      "java.lang.Error",
      "java.lang.RuntimeException",
      "java.lang.NullPointerException",
      "java.lang.ClassCastException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  public BadExceptionThrownInspection() {
    if (!exceptionsString.isEmpty()) {
      exceptions.clear();
      final List<String> strings =
        StringUtil.split(exceptionsString, ",");
      exceptions.addAll(strings);
      exceptionsString = "";
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsBundle.message( "exception.class.column.name")));
    final var panel = new InspectionOptionsPanel();
    panel.addGrowing(UiUtils.createAddRemoveTreeClassChooserPanel(
      InspectionGadgetsBundle.message("choose.exception.class"),
      InspectionGadgetsBundle.message("choose.exception.label"),
      table,
      true,
      CommonClassNames.JAVA_LANG_THROWABLE));
    return panel;
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionThrown";
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