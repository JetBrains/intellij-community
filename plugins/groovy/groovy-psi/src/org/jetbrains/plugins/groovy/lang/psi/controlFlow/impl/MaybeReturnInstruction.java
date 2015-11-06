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

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
* @author peter
*/
public class MaybeReturnInstruction extends InstructionImpl {
  MaybeReturnInstruction(GrExpression element) {
    super(element);
  }

  public String toString() {
    return super.toString() + " MAYBE_RETURN";
  }

  public boolean mayReturnValue() {
    GrExpression expression = (GrExpression) getElement();
    assert expression != null;
    final PsiType type = expression.getType();
    return !PsiType.VOID.equals(type) && !PsiUtil.isVoidMethodCall(expression);
  }

}
