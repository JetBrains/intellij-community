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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyEmptyStatementBodyInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Statement with empty body";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public String buildErrorString(Object... args) {
    if (args[0] instanceof GrIfStatement) {
      return "'#ref' statement has empty branch";
    }
    else {
      return "'#ref' statement has empty body";
    }
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull GrWhileStatement statement) {
      super.visitWhileStatement(statement);
      final GrStatement body = statement.getBody();
      if (body == null) {
        return;
      }
      if (!isEmpty(body)) {
        return;
      }
      registerStatementError(statement, statement);
    }

    @Override
    public void visitForStatement(@NotNull GrForStatement statement) {
      super.visitForStatement(statement);
      final GrStatement body = statement.getBody();
      if (body == null) {
        return;
      }
      if (!isEmpty(body)) {
        return;
      }
      registerStatementError(statement, statement);
    }


    @Override
    public void visitIfStatement(@NotNull GrIfStatement statement) {
      super.visitIfStatement(statement);
      final GrStatement thenBranch = statement.getThenBranch();
      if (thenBranch != null) {
        if (isEmpty(thenBranch)) {
          registerStatementError(statement, statement);
          return;
        }
      }
      final GrStatement elseBranch = statement.getElseBranch();

      if (elseBranch != null) {
        if (isEmpty(elseBranch)) {
          registerStatementError(statement, statement);
        }
      }
    }

    private static boolean isEmpty(GroovyPsiElement body) {
      if (!(body instanceof GrBlockStatement)) {
        return false;
      }
      final GrBlockStatement block = (GrBlockStatement)body;
      final GrOpenBlock openBlock = block.getBlock();

      final PsiElement brace = openBlock.getLBrace();
      if (brace == null) return false;
      final PsiElement nextNonWhitespace = PsiUtil.skipWhitespaces(brace.getNextSibling(), true);
      return nextNonWhitespace == openBlock.getRBrace();
    }
  }
}
