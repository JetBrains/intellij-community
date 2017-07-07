/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyLongMethodInspectionBase extends GroovyMethodMetricInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return "Overly long method";
  }

  @Override
  protected int getDefaultLimit() {
    return 30;
  }

  @Override
  protected String getConfigurationLabel() {
    return "Maximum statements per method:";
  }

  @Override
  public String buildErrorString(Object... args) {
    return "Method '#ref' is too long ( statement count =" + args[0] + '>' + args[1] + ')';
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod method) {
      super.visitMethod(method);
      final int limit = getLimit();
      final StatementCountVisitor visitor = new StatementCountVisitor();
      final GrOpenBlock block = method.getBlock();
      if (block == null) return;
      block.accept(visitor);
      final int statementCount = visitor.getStatementCount();
      if (statementCount <= limit) {
        return;
      }
      registerMethodError(method, statementCount, limit);
    }
  }
}
