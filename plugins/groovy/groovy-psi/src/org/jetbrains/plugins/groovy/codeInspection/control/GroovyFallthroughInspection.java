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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression;

import java.util.regex.Pattern;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.skipWhiteSpacesAndNewLines;

public class GroovyFallthroughInspection extends BaseInspection {

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.fallthrough.in.switch.statement");
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
      visitSwitchElement(switchStatement);
    }

    @Override
    public void visitSwitchExpression(@NotNull GrSwitchExpression switchExpression) {
      visitSwitchElement(switchExpression);
    }

    public void visitSwitchElement(@NotNull GrSwitchElement switchElement) {
      if (switchElement instanceof GrSwitchStatement) {
        super.visitSwitchStatement((GrSwitchStatement)switchElement);
      } else if (switchElement instanceof GrSwitchExpression) {
        super.visitSwitchExpression((GrSwitchExpression)switchElement);
      }
      final GrCaseSection[] caseSections = switchElement.getCaseSections();
      if (ContainerUtil.exists(caseSections, section -> section.getArrow() != null)) {
        return;
      }
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
      final PsiElement element = skipWhiteSpacesAndNewLines(caseClause, PsiTreeUtil::prevLeaf);
      if (!(element instanceof PsiComment)) {
        return false;
      }
      final String commentText = element.getText();
      return commentPattern.matcher(commentText).find();
    }
  }
}
