/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class InlineLocalVarSettings {
  private final GrExpression myInitializer;
  private final int myWriteInstructionNumber;
  private final Instruction[] myFlow;

  public InlineLocalVarSettings(GrExpression initializer, int writeInstructionNumber, Instruction[] flow) {
    myWriteInstructionNumber = writeInstructionNumber;
    myFlow = flow;
    final PsiElement psiElement = PsiUtil.skipParentheses(initializer, false);
    if (psiElement instanceof GrExpression) {
      myInitializer = (GrExpression)psiElement;
    }
    else {
      myInitializer = initializer;
    }
  }

  public GrExpression getInitializer() {
    return myInitializer;
  }

  public int getWriteInstructionNumber() {
    return myWriteInstructionNumber;
  }

  public Instruction[] getFlow() {
    return myFlow;
  }
}
