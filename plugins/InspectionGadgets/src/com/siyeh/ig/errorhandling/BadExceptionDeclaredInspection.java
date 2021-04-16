// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class BadExceptionDeclaredInspection extends BaseInspection {

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

  @SuppressWarnings("PublicField")
  public boolean ignoreTestCases = false; // keep for compatibility
  @SuppressWarnings("PublicField")
  public boolean ignoreLibraryOverrides = false;

  public BadExceptionDeclaredInspection() {
    if (!exceptionsString.isEmpty()) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      exceptions.addAll(strings);
      exceptionsString = "";
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsBundle.message("exception.class.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(
        InspectionGadgetsBundle.message("choose.exception.class"),
        InspectionGadgetsBundle.message("choose.exception.label"),
        table,
        true,
        CommonClassNames.JAVA_LANG_THROWABLE);

    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addGrowing(tablePanel);
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.exceptions.declared.on.library.override.option"), "ignoreLibraryOverrides");
    return panel;
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionDeclared";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bad.exception.declared.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionDeclaredVisitor();
  }

  private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement reference : references) {
        final PsiElement element = reference.resolve();
        if (!(element instanceof PsiClass)) {
          continue;
        }
        final PsiClass thrownClass = (PsiClass)element;
        final String qualifiedName = thrownClass.getQualifiedName();
        if (qualifiedName != null && exceptions.contains(qualifiedName)) {
          registerError(reference, reference);
        }
      }
    }
  }
}