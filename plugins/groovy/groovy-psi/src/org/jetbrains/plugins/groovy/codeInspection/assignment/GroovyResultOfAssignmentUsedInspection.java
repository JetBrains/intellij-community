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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class GroovyResultOfAssignmentUsedInspection extends BaseInspection {
  /**
   * @noinspection PublicField, WeakerAccess
   */
  public boolean inspectClosures = false;

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      checkbox("inspectClosures", GroovyBundle.message("checkbox.inspect.anonymous.closures")));
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.nested.assignment");
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull GrAssignmentExpression grAssignmentExpression) {
      super.visitAssignmentExpression(grAssignmentExpression);
      if (isConfusingAssignmentUsage(grAssignmentExpression)) {
        registerError(grAssignmentExpression);
      }
    }

    private boolean isConfusingAssignmentUsage(PsiElement expr) {
      PsiElement parent = expr.getParent();
      return !(parent instanceof GroovyFile) &&
             (inspectClosures || !(parent instanceof GrClosableBlock)) &&
             PsiUtil.isExpressionUsed(expr);
    }
  }
}
