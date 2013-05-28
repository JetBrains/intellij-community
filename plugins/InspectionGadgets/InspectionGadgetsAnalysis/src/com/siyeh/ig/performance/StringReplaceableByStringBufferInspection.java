/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StringReplaceableByStringBufferInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnLoop = true;

  @Override
  @NotNull
  public String getID() {
    return "NonConstantStringShouldBeStringBuffer";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.replaceable.by.string.buffer.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.replaceable.by.string.buffer.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "string.replaceable.by.string.buffer.in.loop.option"),
                                          this, "onlyWarnOnLoop");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringReplaceableByStringBufferVisitor();
  }

  private class StringReplaceableByStringBufferVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(
      @NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock =
        PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (codeBlock == null) {
        return;
      }
      final PsiType type = variable.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING,
                                type)) {
        return;
      }
      if (!variableIsAppendedTo(variable, codeBlock)) {
        return;
      }
      registerVariableError(variable);
    }

    public boolean variableIsAppendedTo(PsiVariable variable,
                                        PsiElement context) {
      final StringVariableIsAppendedToVisitor visitor =
        new StringVariableIsAppendedToVisitor(variable,
                                              onlyWarnOnLoop);
      context.accept(visitor);
      return visitor.isAppendedTo();
    }
  }
}