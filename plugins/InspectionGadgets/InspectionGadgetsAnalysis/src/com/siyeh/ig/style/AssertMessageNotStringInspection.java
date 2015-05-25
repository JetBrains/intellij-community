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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class AssertMessageNotStringInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnBoolean = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assert.message.not.string.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("assert.message.of.type.boolean.problem.descriptor", type.getPresentableText());
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("assert.message.not.string.only.warn.boolean.option"),
                                          this, "onlyWarnOnBoolean");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertMessageNotStringVisitor();
  }

  private class AssertMessageNotStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression assertDescription = statement.getAssertDescription();
      if (assertDescription == null) {
        return;
      }
      final PsiType type = assertDescription.getType();
      if (onlyWarnOnBoolean) {
        if (PsiType.BOOLEAN.equals(type)) {
          registerError(assertDescription, type);
          return;
        }
        final PsiClassType javaLangBoolean = PsiType.BOOLEAN.getBoxedType(statement);
        if (javaLangBoolean != null && javaLangBoolean.equals(type)) {
          registerError(assertDescription, type);
        }
      }
      else {
        if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
          registerError(assertDescription, type);
        }
      }
    }
  }
}
