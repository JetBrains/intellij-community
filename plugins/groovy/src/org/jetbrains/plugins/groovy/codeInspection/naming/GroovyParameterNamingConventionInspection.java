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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrForStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public class GroovyParameterNamingConventionInspection extends ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 4;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @NotNull
  public String getDisplayName() {
    return "Method parameter naming convention";
  }

  protected GroovyFix buildFix(PsiElement location) {
    return new RenameFix();
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @NotNull
  public String buildErrorString(Object... args) {
    final String className = (String) args[0];
    if (className.length() < getMinLength()) {
      return "Method parameter name '#ref' is too short";
    } else if (className.length() > getMaxLength()) {
      return "Method parameter name '#ref' is too long";
    }
    return "Method parameter name '#ref' doesn't match regex '" + getRegex() + "' #loc";
  }

  protected String getDefaultRegex() {
    return "[a-z][A-Za-z\\d]*";
  }

  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {
    public void visitParameter(GrParameter grParameter) {
      super.visitParameter(grParameter);
      final String name = grParameter.getName();
      final PsiElement scope = grParameter.getDeclarationScope();
      if (scope instanceof GrCatchClause ||
          scope instanceof GrForStatement) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerVariableError(grParameter, name);
    }
  }
}