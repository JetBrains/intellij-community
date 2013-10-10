/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class MagicCharacterInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("magic.character.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "magic.character.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CharacterLiteralsShouldBeExplicitlyDeclaredVisitor();
  }

  private static class CharacterLiteralsShouldBeExplicitlyDeclaredVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiType.CHAR)) {
        return;
      }
      final String text = expression.getText();
      if (text == null) {
        return;
      }
      if (text.equals(" ")) {
        return;
      }
      if (ExpressionUtils.isDeclaredConstant(expression)) {
        return;
      }
      if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
        return;
      }
      registerError(expression);
    }
  }
}
