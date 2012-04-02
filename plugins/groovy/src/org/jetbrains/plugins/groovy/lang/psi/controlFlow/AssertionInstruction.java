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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

/**
 * @author peter
 */
public class AssertionInstruction extends InstructionImpl implements MixinTypeInstruction {
  private final boolean myNegate;

  public AssertionInstruction(int num, GrExpression assertion, boolean negate) {
    super(assertion, num);
    myNegate = negate;
  }

  public boolean isNegate() {
    return myNegate;
  }

  protected String getElementPresentation() {
    return "assertion: " + (myNegate ? "! " : "") + getElement().getText();
  }

  @Nullable
  private GrInstanceOfExpression getApplicableInstanceofOrNull() {
    final PsiElement element = getElement();
    if (element instanceof GrInstanceOfExpression && !isNegate()) {
      GrExpression operand = ((GrInstanceOfExpression)element).getOperand();
      final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
      if (operand instanceof GrReferenceExpression && ((GrReferenceExpression)operand).getQualifier() == null && typeElement != null) {
        return (GrInstanceOfExpression)element;
      }
    }
    return null;
  }

  @Nullable
  public PsiType inferMixinType() {
    GrInstanceOfExpression instanceOf = getApplicableInstanceofOrNull();
    if (instanceOf == null) return null;

    return instanceOf.getTypeElement().getType();
  }

  @Override
  public ReadWriteVariableInstruction getInstructionToMixin(Instruction[] flow) {
    GrInstanceOfExpression instanceOf = getApplicableInstanceofOrNull();
    if (instanceOf == null) return null;

    Instruction instruction = ControlFlowUtils.findInstruction(instanceOf.getOperand(), flow);
    if (instruction instanceof ReadWriteVariableInstruction) {
      return (ReadWriteVariableInstruction)instruction;
    }
    return null;
  }

  @Nullable
  @Override
  public String getVariableName() {
    GrInstanceOfExpression instanceOf = getApplicableInstanceofOrNull();
    if (instanceOf == null) return null;

    return instanceOf.getOperand().getText();
  }
}
