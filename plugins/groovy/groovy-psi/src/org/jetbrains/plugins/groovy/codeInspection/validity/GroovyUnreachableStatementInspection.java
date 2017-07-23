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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public class GroovyUnreachableStatementInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unreachable Statement";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Unreachable statement #loc";

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
    @Override
    public void visitClosure(@NotNull GrClosableBlock closure) {
      super.visitClosure(closure);
      GrStatement[] statements = closure.getStatements();
      for (int i = 0; i < statements.length - 1; i++) {
        checkPair(statements[i], statements[i+1]);
      }
    }

    @Override
    public void visitOpenBlock(@NotNull GrOpenBlock block) {
      super.visitOpenBlock(block);
      GrStatement[] statements = block.getStatements();
      for (int i = 0; i < statements.length - 1; i++) {
        checkPair(statements[i], statements[i+1]);
      }
    }

    private void checkPair(GrStatement prev, GrStatement statement) {
      if (!ControlFlowUtils.statementMayCompleteNormally(prev)) {
        registerError(statement);
      }
    }
  }
}
