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
package org.jetbrains.plugins.groovy.codeInspection.control;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;

public class GroovyFallthroughInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Fallthrough in switch statement";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Fallthough in switch statement #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitSwitchStatement(GrSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);
      final GrCaseSection[] grCaseSections = switchStatement.getCaseSections();
      for (int i = 0; i < grCaseSections.length - 1; i++) {
        final GrCaseSection caseSection = grCaseSections[i];
        final GrStatement[] statements = caseSection.getStatements();
        if (statements.length == 0) {
          continue;
        }
        final GrStatement lastStatement = statements[statements.length - 1];
        if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
          registerError(grCaseSections[i + 1].getFirstChild());
        }
      }
    }
  }
}