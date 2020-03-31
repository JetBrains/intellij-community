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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

class StatementCountVisitor extends PsiRecursiveElementWalkingVisitor {

  private int statementCount = 0;

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (element instanceof GrBlockStatement || element instanceof GrOpenBlock) {
      super.visitElement(element);
    }
    else if (element instanceof GrStatement) {
      if (element.getParent() instanceof GrStatementOwner) {
        statementCount++;
      }
      super.visitElement(element);
    }
  }

  public int getStatementCount() {
    return statementCount;
  }
}
