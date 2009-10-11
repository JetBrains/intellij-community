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
package org.jetbrains.plugins.groovy.lang.completion.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.inline.InlineMethodConflictSolver;

/**
 * @author ven
 */
public class SuggestedVariableNamesGetter implements ContextGetter {
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    //complete variable names
    if (context != null) {
      final PsiElement parent = context.getParent();
      if (parent instanceof GrVariable) {
        final GrVariable variable = (GrVariable) parent;
        if (context.equals(variable.getNameIdentifierGroovy())) {
          final PsiType type = variable.getTypeGroovy();
          if (type != null) {
            final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.getProject());
            VariableKind kind = variable instanceof GrParameter ? VariableKind.PARAMETER :
                variable instanceof GrField ? VariableKind.FIELD : VariableKind.LOCAL_VARIABLE;
            SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(kind, null, null, type);
            String[] names = suggestedNameInfo.names;
            if (names.length > 0) {
              String name = names[0];
              String newName = InlineMethodConflictSolver.suggestNewName(name, null, parent);
              if (!name.equals(newName)) {
                return new String[]{newName};
              }
            }
            return names;
          }
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
