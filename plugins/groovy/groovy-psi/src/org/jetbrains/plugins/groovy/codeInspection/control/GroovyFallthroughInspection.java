/*
 * Copyright 2007-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;

import java.util.regex.Pattern;

public class GroovyFallthroughInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Fallthrough in switch statement";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Fallthrough in switch statement #loc";

  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    private static final Pattern commentPattern = Pattern.compile("(?i)falls?\\s*thro?u");

    @Override
    public void visitSwitchStatement(@NotNull GrSwitchStatement switchStatement) {
      super.visitSwitchStatement(switchStatement);
      final GrCaseSection[] caseSections = switchStatement.getCaseSections();
      for (int i = 1; i < caseSections.length; i++) {
        final GrCaseSection caseSection = caseSections[i];
        if (isCommented(caseSection)) {
          continue;
        }
        final GrCaseSection previousCaseSection = caseSections[i - 1];
        final GrStatement[] statements = previousCaseSection.getStatements();
        if (statements.length == 0) {
          registerError(caseSection.getFirstChild());
        }
        else {
          final GrStatement lastStatement = statements[statements.length - 1];
          if (ControlFlowUtils.statementMayCompleteNormally(lastStatement)) {
            registerError(caseSection.getFirstChild());
          }
        }
      }
    }

    private static boolean isCommented(GrCaseSection caseClause) {
      final PsiElement element = PsiTreeUtil.skipWhitespacesBackward(caseClause);
      if (!(element instanceof PsiComment)) {
        return false;
      }
      final String commentText = element.getText();
      return commentPattern.matcher(commentText).find();
    }
  }
}