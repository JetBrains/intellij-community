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
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

class StatementCountVisitor extends GroovyRecursiveElementVisitor {
  private int statementCount = 0;

  @Override
  public void visitElement(GroovyPsiElement element) {
    int oldCount = 0;
    if (element instanceof GrMethod) {
      oldCount = statementCount;
    }
    super.visitElement(element);

    if (element instanceof GrMethod) {
      statementCount = oldCount;
    }
  }

  @Override
  public void visitStatement(@NotNull GrStatement statement) {
    super.visitStatement(statement);
    if (statement instanceof GrBlockStatement) {
      return;
    }
    statementCount++;
  }


  public int getStatementCount() {
    return statementCount;
  }
}
