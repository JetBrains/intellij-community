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
package com.siyeh.ig.naming;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class LocalVariableNamingConventionInspectionBase extends ConventionInspection {
  private static final int DEFAULT_MIN_LENGTH = 1;
  private static final int DEFAULT_MAX_LENGTH = 20;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreForLoopParameters = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCatchParameters = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("local.variable.naming.convention.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String varName = (String)infos[0];
    if (varName.length() < getMinLength()) {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.short");
    }
    else if (varName.length() > getMaxLength()) {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.long");
    }
    else {
      return InspectionGadgetsBundle.message("local.variable.naming.convention.problem.descriptor.regex.mismatch", getRegex());
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (m_ignoreForLoopParameters) {
        final PsiElement parent = variable.getParent();
        if (parent != null) {
          final PsiElement grandparent = parent.getParent();
          if (grandparent instanceof PsiForStatement) {
            final PsiForStatement forLoop = (PsiForStatement)grandparent;
            final PsiStatement initialization = forLoop.getInitialization();
            if (parent.equals(initialization)) {
              return;
            }
          }
        }
      }
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable, name);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      final PsiElement scope = variable.getDeclarationScope();
      final boolean isCatchParameter = scope instanceof PsiCatchSection;
      final boolean isForeachParameter = scope instanceof PsiForeachStatement;
      if (!isCatchParameter && !isForeachParameter) {
        return;
      }
      if (m_ignoreCatchParameters && isCatchParameter) {
        return;
      }
      if (m_ignoreForLoopParameters && isForeachParameter) {
        return;
      }
      final String name = variable.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(variable, name);
    }
  }
}
