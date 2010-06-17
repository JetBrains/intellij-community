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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.AssertionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.TypeInferenceHelper;

import java.util.Arrays;
import java.util.Map;

/**
 * @author ven
 */
public class TypeDfaInstance implements DfaInstance<Map<String, PsiType>> {
  public void fun(Map<String, PsiType> map, Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        final Computable<PsiType> computation = getInitializerTypeComputation(element);
        if (computation != null) {
          final TypeInferenceHelper helper = GroovyPsiManager.getInstance(element.getProject()).getTypeInferenceHelper();

          final PsiType type = helper.doInference(computation, map);

          map.put(((ReadWriteVariableInstruction) instruction).getVariableName(), type);
        }
      }
    }
    if (instruction instanceof AssertionInstruction) {
      final AssertionInstruction assertionInstruction = (AssertionInstruction)instruction;
      final PsiElement element = assertionInstruction.getElement();
      if (element instanceof GrInstanceOfExpression && !assertionInstruction.isNegate()) {
        final GrExpression operand = ((GrInstanceOfExpression)element).getOperand();
        final GrTypeElement typeElement = ((GrInstanceOfExpression)element).getTypeElement();
        if (typeElement != null) {
          map.put(operand.getText(), typeElement.getType());
        }
      }
    }
  }

  @Nullable
  private static Computable<PsiType> getInitializerTypeComputation(final PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof GrAssignmentExpression) {
        final GrExpression initializer = ((GrAssignmentExpression)parent).getRValue();
        if (initializer != null) {
          return new Computable<PsiType>() {
            @Nullable
            public PsiType compute() {
              return initializer.getType();
            }
          };
        }
        return null;
      }

      if (parent instanceof GrListOrMap) {
        GrListOrMap list = (GrListOrMap)parent;
        if (list.getParent() instanceof GrAssignmentExpression) { // multiple assignment
          final GrExpression rValue = ((GrAssignmentExpression) list.getParent()).getRValue();
          int idx = Arrays.asList(list.getInitializers()).indexOf(element);
          if (idx >= 0 && rValue != null) {
            return getMultipleAssignmentTypeComputation(rValue, idx);
          }
        }
      }

      return null;
    }

    if (element instanceof GrVariable) {
      return new Computable<PsiType>() {
        @Nullable
        public PsiType compute() {
          GrVariable variable = (GrVariable)element;
          if (!(variable instanceof GrParameter)) {
            final GrExpression initializer = variable.getInitializerGroovy();
            if (initializer != null) {
              return initializer.getType();
            }
          }
          return variable.getTypeGroovy();
        }
      };
    }

    return null;
  }

  private static Computable<PsiType> getMultipleAssignmentTypeComputation(final GrExpression rValue, final int idx) {
    return new Computable<PsiType>() {
      @Nullable
      public PsiType compute() {
        PsiType rType = rValue.getType();
        if (rType instanceof GrTupleType) {
          PsiType[] componentTypes = ((GrTupleType) rType).getComponentTypes();
          if (idx < componentTypes.length) return componentTypes[idx];
          return null;
        }
        return PsiUtil.extractIterableTypeParameter(rType, false);
      }
    };
  }

  @NotNull
  public Map<String, PsiType> initial() {
    return new HashMap<String, PsiType>();
  }

  public boolean isForward() {
    return true;
  }
}
