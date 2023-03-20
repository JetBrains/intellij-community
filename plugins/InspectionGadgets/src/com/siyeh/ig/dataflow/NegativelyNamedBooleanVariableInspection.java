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
package com.siyeh.ig.dataflow;

import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.InvertBooleanFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NegativelyNamedBooleanVariableInspection extends BaseInspection {

  @NonNls
  private static final String[] NEGATIVE_NAMES = {"non", "not", "isNot", "isNon", "shouldNot", "shallNot", "willNot", "cannot", "canNot",
    "cant", "hasNot", "hasnt", "couldNot", "doesNot", "hidden", "isHidden", "disabled", "isDisabled", "isInvalid", "invalid"};

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    return new InvertBooleanFix(InspectionGadgetsBundle.message("invert.quickfix", variable.getName()));
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("negatively.named.boolean.variable.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegativelyNamedBooleanVariableVisitor();
  }

  private static class NegativelyNamedBooleanVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      if (!PsiTypes.booleanType().equals(variable.getType())) {
        return;
      }
      if (variable instanceof PsiParameter && variable.getParent() instanceof PsiForeachStatement) {
        return;
      }
      final String name = variable.getName();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
      final String prefix = codeStyleManager.getPrefixByVariableKind(codeStyleManager.getVariableKind(variable));
      for (final String negativeName : NEGATIVE_NAMES) {
        assert name != null;
        if (isNegativelyNamed(name, negativeName) || !prefix.isEmpty() && isNegativelyNamed(name, prefix + negativeName)) {
          registerVariableError(variable, variable);
          break;
        }
      }
    }

    private static boolean isNegativelyNamed(String name, String negativeName) {
      if (!name.startsWith(negativeName) ||
          (name.length() != negativeName.length() && !Character.isUpperCase(name.charAt(negativeName.length())))) {
        return false;
      }
      return true;
    }
  }
}
