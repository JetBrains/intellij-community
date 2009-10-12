/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;

/**
 * @author ven
*/
class ReadWriteVariableInstructionImpl extends InstructionImpl implements ReadWriteVariableInstruction {
  private final boolean myIsWrite;
  public String myName;

  ReadWriteVariableInstructionImpl(String varName, PsiElement element, int num, boolean isWrite) {
    super(element, num);
    myName = varName;
    myIsWrite = isWrite;
  }

  ReadWriteVariableInstructionImpl(PsiVariable variable, int num) {
    super(variable, num);
    myName = variable.getName();
    myIsWrite = true;
  }

  ReadWriteVariableInstructionImpl(GrReferenceExpression refExpr, int num, boolean isWrite) {
    super(refExpr, num);
    myName = refExpr.getReferenceName();
    myIsWrite = isWrite;
  }

  public String getVariableName() {
    return myName;
  }

  public boolean isWrite() {
    return myIsWrite;
  }

  protected String getElementPresentation() {
    return (isWrite() ? "WRITE " : "READ ") + myName;
  }
}
