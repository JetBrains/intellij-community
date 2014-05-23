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
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;

class ReturnPointCountVisitor extends GroovyRecursiveElementVisitor {

  private int m_count = 0;

  @Override
  public void visitClosure(GrClosableBlock grClosableBlock) {
    //so as not to drill down
  }

  @Override
  public void visitReturnStatement(@NotNull GrReturnStatement statement) {
    super.visitReturnStatement(statement);
    m_count++;
  }

  public int getCount() {
    return m_count;
  }
}
