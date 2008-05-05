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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashMap;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
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
      final GrExpression initializer = getInitializer(element);
      if (initializer != null) {
        final TypeInferenceHelper helper = GroovyPsiManager.getInstance(initializer.getProject()).getTypeInferenceHelper();

        final PsiType type = helper.doInference(new Computable<PsiType>() {
          public PsiType compute() {
            return initializer.getType();
          }
        }, map);

        if (type != null) {
          map.put(((ReadWriteVariableInstruction) instruction).getVariableName(), type);
        }
      }
    }
  }

  private GrExpression getInitializer(PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression) element).getQualifierExpression() == null) {
      if (element.getParent() instanceof GrAssignmentExpression) {
          return ((GrAssignmentExpression) element.getParent()).getRValue();
      } else if (element.getParent() instanceof GrListOrMap) {
        GrListOrMap list = (GrListOrMap) element.getParent();
        if (list.getParent() instanceof GrAssignmentExpression) {
          GrAssignmentExpression assignment = (GrAssignmentExpression) list.getParent(); //multiple assignment
          if (assignment.getRValue() instanceof GrListOrMap) {
            GrListOrMap rList = (GrListOrMap) assignment.getRValue();
            if (rList != null && !rList.isMap()) {
              int idx = -1;
              GrExpression[] initializers = list.getInitializers();
              for (int i = 0; i < initializers.length; i++) {
                GrExpression initializer = initializers[i];
                if (element == initializer) {
                   idx = i;
                  break;
                }
              }
              if (idx >= 0) {
                GrExpression[] rInitializers = rList.getInitializers();
                if (idx < rInitializers.length) return rInitializers[idx];
              }
            }
          }
        }
      }
    } else if (element instanceof GrVariable && !(element instanceof GrParameter)) {
      return ((GrVariable) element).getInitializerGroovy();
    }

    return null;
  }

  @NotNull
  public Map<String, PsiType> initial() {
    return new HashMap<String, PsiType>();
  }

  public boolean isForward() {
    return true;
  }
}
