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
package org.jetbrains.plugins.groovy.codeInspection.validity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression;

import java.util.*;

public final class GroovyDuplicateSwitchBranchInspection extends BaseInspection {

  @Override
  protected @Nullable String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.duplicate.switch.case.ref");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    private void visitSwitchElement(@NotNull GrSwitchElement switchElement) {
      final Set<GrExpression> duplicateExpressions = new HashSet<>();
      final GrCaseSection[] sections = switchElement.getCaseSections();
      final List<GrExpression> allMatchingExpressions = new ArrayList<>(sections.length);
      for (final GrCaseSection label : sections) {
        final GrExpression[] expressions = label.getExpressions();
        allMatchingExpressions.addAll(Arrays.asList(expressions));
      }
      for (int i = 0; i < allMatchingExpressions.size(); ++i) {
        for (int j = i + 1; j < allMatchingExpressions.size(); ++j) {
          // this has complexity O(n^2). I assume there are not so many matching expressions,
          // so we may be happy with this straightforward algorithm
          if (EquivalenceChecker.expressionsAreEquivalent(allMatchingExpressions.get(i), allMatchingExpressions.get(j))) {
            duplicateExpressions.add(allMatchingExpressions.get(i));
            duplicateExpressions.add(allMatchingExpressions.get(j));
          }
        }
      }
      for (GrExpression duplicateExpression : duplicateExpressions) {
        registerError(duplicateExpression);
      }
    }


    @Override
    public void visitSwitchExpression(@NotNull GrSwitchExpression switchExpression) {
      super.visitSwitchExpression(switchExpression);
      visitSwitchElement(switchExpression);
    }

    @Override
    public void visitSwitchStatement(@NotNull GrSwitchStatement grSwitchStatement) {
      super.visitSwitchStatement(grSwitchStatement);
      visitSwitchElement(grSwitchStatement);
    }
  }
}
