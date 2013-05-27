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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class BadExceptionCaughtInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public String exceptionsString = "";

  /**
   * @noinspection PublicField
   */
  public final ExternalizableStringSet exceptions =
    new ExternalizableStringSet(
      "java.lang.NullPointerException",
      "java.lang.IllegalMonitorStateException",
      "java.lang.ArrayIndexOutOfBoundsException"
    );

  public BadExceptionCaughtInspection() {
    if (exceptionsString.length() != 0) {
      exceptions.clear();
      final List<String> strings = StringUtil.split(exceptionsString, ",");
      for (String string : strings) {
        exceptions.add(string);
      }
      exceptionsString = "";
    }
  }

  @Override
  @NotNull
  public String getID() {
    return "ProhibitedExceptionCaught";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bad.exception.caught.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("bad.exception.caught.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsBundle.message("exception.class.column.name")));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.exception.class"),
                                                        "java.lang.Throwable");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadExceptionCaughtVisitor();
  }

  private class BadExceptionCaughtVisitor extends BaseInspectionVisitor {

    @Override
    public void visitCatchSection(PsiCatchSection section) {
      super.visitCatchSection(section);
      final PsiParameter parameter = section.getParameter();
      if (parameter == null) {
        return;
      }
      final PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiTypeElement[] childTypeElements = PsiTreeUtil.getChildrenOfType(typeElement, PsiTypeElement.class);
      if (childTypeElements != null) {
        for (PsiTypeElement childTypeElement : childTypeElements) {
          checkTypeElement(childTypeElement);
        }
      }
      else {
        checkTypeElement(typeElement);
      }
    }

    private void checkTypeElement(PsiTypeElement typeElement) {
      final PsiType type = typeElement.getType();
      if (exceptions.contains(type.getCanonicalText())) {
        registerError(typeElement);
      }
    }
  }
}
