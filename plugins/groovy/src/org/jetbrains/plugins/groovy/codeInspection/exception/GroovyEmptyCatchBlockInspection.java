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
package org.jetbrains.plugins.groovy.codeInspection.exception;

import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

public class GroovyEmptyCatchBlockInspection extends BaseInspection {

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return ERROR_HANDLING;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Empty 'catch' block";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitCatchClause(GrCatchClause catchClause) {
      super.visitCatchClause(catchClause);
      final GrOpenBlock body = catchClause.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }

      final GrParameter parameter = catchClause.getParameter();
      if (parameter == null) return;
      if (GrExceptionUtil.ignore(parameter)) return;

      final LocalQuickFix[] fixes = {new RenameElementFix(parameter, "ignored")};
      registerError(catchClause.getFirstChild(), "Empty '#ref' block #loc", fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private static boolean isEmpty(@NotNull GrOpenBlock body) {
      final GrStatement[] statements = body.getStatements();
      return statements.length == 0;
    }
  }
}