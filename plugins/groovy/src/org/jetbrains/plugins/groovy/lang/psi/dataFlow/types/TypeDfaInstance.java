/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.TypeInferenceHelper;

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
  }

  private Computable<PsiType> getInitializerTypeComputation(PsiElement element) {
    GrExpression initializer = null;
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null) {
      if (element.getParent() instanceof GrAssignmentExpression) {
        initializer = ((GrAssignmentExpression) element.getParent()).getRValue();
      } else if (element.getParent() instanceof GrListOrMap) {
        GrListOrMap list = (GrListOrMap) element.getParent();
        if (list.getParent() instanceof GrAssignmentExpression) {
          GrAssignmentExpression assignment = (GrAssignmentExpression) list.getParent(); //multiple assignment
          int idx = -1;
          GrExpression[] initializers = list.getInitializers();
          for (int i = 0; i < initializers.length; i++) {
            if (element == initializers[i]) {
              idx = i;
              break;
            }
          }
          if (idx >= 0) {
            final GrExpression rValue = assignment.getRValue();
            if (rValue != null) {
              return getMultipleAssignmentTypeComputation(rValue, idx);
            }
          }
        }
      }
    } else if (element instanceof GrVariable && !(element instanceof GrParameter)) {
      initializer = ((GrVariable) element).getInitializerGroovy();
    }

    final GrExpression initializer1 = initializer;
    return initializer == null ? null : new Computable<PsiType>() {
      public PsiType compute() {
        return initializer1.getType();
      }
    };
  }

  private Computable<PsiType> getMultipleAssignmentTypeComputation(final GrExpression rValue, final int idx) {
    return new Computable<PsiType>() {
      public PsiType compute() {
        PsiType rType = rValue.getType();
        if (rType == null) return null;
        if (rType instanceof GrTupleType) {
          PsiType[] componentTypes = ((GrTupleType) rType).getComponentTypes();
          if (idx < componentTypes.length) return componentTypes[idx];
        } else if (rType instanceof PsiClassType) {
          PsiClassType.ClassResolveResult result = ((PsiClassType) rType).resolveGenerics();
          PsiClass clazz = result.getElement();
          if (clazz != null) {
            PsiClass listClass = rValue.getManager().findClass("java.util.List", rValue.getResolveScope());
            if (listClass != null && listClass.getTypeParameters().length == 1) {
              PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(listClass, clazz, result.getSubstitutor());
              if (substitutor != null) {
                return substitutor.substitute(listClass.getTypeParameters()[0]);
              }
            }
          }
        }
        return null;
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
