/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.naming;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public class GroovyLocalVariableNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  @NotNull
  public String getDisplayName() {
    return "Local variable naming convention";
  }

  @Override
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return GroovyQuickFixFactory.getInstance().createRenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... args) {
    final String className = (String) args[0];
    if (className.length() < getMinLength()) {
      return "Local variable name '#ref' is too short";
    } else if (className.length() > getMaxLength()) {
      return "Local variable name '#ref' is too long";
    }
    return "Local variable name '#ref' doesn't match regex '" + getRegex() + "' #loc";
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

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {
    @Override
    public void visitVariable(GrVariable grVariable) {
      super.visitVariable(grVariable);
      if (grVariable instanceof GrField || grVariable instanceof GrParameter) {
        return;
      }
      final String name = grVariable.getName();
      if (isValid(name)) {
        return;
      }
      registerVariableError(grVariable, name);
    }

    @Override
    public void visitParameter(GrParameter grParameter) {
      super.visitParameter(grParameter);
      final String name = grParameter.getName();
      final PsiElement scope = grParameter.getDeclarationScope();
      if (!(scope instanceof GrCatchClause) &&
          !(scope instanceof GrForStatement)) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(grParameter, name);
    }
  }
}