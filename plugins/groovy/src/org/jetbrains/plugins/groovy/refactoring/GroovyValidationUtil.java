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
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GroovyValidationUtil {

  private GroovyValidationUtil() {
  }

  public static boolean validateNewParameterName(GrParameter variable, MultiMap<PsiElement,String> conflicts, @NotNull String varName) {
    GrParameterList list = PsiTreeUtil.getParentOfType(variable, GrParameterList.class);
    GrParametersOwner owner = PsiTreeUtil.getParentOfType(variable, GrParametersOwner.class);
    assert owner != null;
    for (GrParameter parameter : list.getParameters()) {
      if (parameter.equals(variable)) continue;
      validateVariableOccurrencesDownImpl(parameter, conflicts, varName);
    }
    validateVariableOccurrencesDown(owner, list, conflicts, varName);
    PsiElement parent = owner.getParent();
    validateVariableOccurrencesUp(parent, owner, conflicts, varName, parent instanceof GroovyFile);
    return conflicts.isEmpty();
  }


  private static void validateVariableOccurrencesUp(PsiElement parent,
                                                    PsiElement lastParent,
                                                    MultiMap<PsiElement, String> conflicts,
                                                    @NotNull String varName,
                                                    final boolean containerIsFile) {
    if (!containerIsFile && (parent instanceof PsiFile) || parent == null) return;

    PsiElement child = parent.getFirstChild();
    while (child != null && child != lastParent) { // Upper variable declarations
      if (child instanceof GrVariableDeclaration) {
        for (GrVariable variable : ((GrVariableDeclaration)child).getVariables()) {
          if (varName.equals(variable.getName())) {
            addConflict(varName, variable, conflicts);
          }
        }
      }
      child = child.getNextSibling();
    }
    if (parent instanceof GrParametersOwner) { //method or closure parameters
      GrParametersOwner owner = (GrParametersOwner)parent;
      for (GrParameter parameter : owner.getParameters()) {
        if (varName.equals(parameter.getName())) {
          addConflict(varName, parameter, conflicts);
        }
      }
    } else if (parent instanceof GrForStatement) { // For statement binding
      GrForStatement statement = (GrForStatement)parent;
      GrForClause clause = statement.getClause();
      if (clause != null) {
        final GrVariable variable = clause.getDeclaredVariable();
        if (variable != null && varName.equals(variable.getName())) {
          addConflict(varName, variable, conflicts);
        }
      }
    }
    if (parent instanceof PsiFile) return;
    validateVariableOccurrencesUp(parent.getParent(), parent, conflicts, varName, false);
  }


  private static void validateVariableOccurrencesDown(PsiElement parent,
                                                      PsiElement startChild,
                                                      MultiMap<PsiElement, String> conflicts,
                                                      @NotNull String varName) {
    PsiElement child = parent.getLastChild();
    while (child != null && child != startChild && !(child instanceof GrTypeDefinition)) {
      validateVariableOccurrencesDownImpl(child, conflicts, varName);
      child = child.getPrevSibling();
    }
  }

  private static void validateVariableOccurrencesDownImpl(final PsiElement child, final MultiMap<PsiElement, String> conflicts, final String varName) {
    if (child instanceof PsiNamedElement) {
      PsiNamedElement element = (PsiNamedElement)child;
      if (varName.equals(element.getName())) {
        addConflict(varName, element, conflicts);
      } else {
        for (PsiElement psiElement : child.getChildren()) {
          if (!(child instanceof GrTypeDefinition)) {
            validateVariableOccurrencesDownImpl(psiElement, conflicts, varName);
          }
        }
      }
    }
  }

  private static void addConflict(final String varName, final PsiNamedElement element, final MultiMap<PsiElement, String> conflicts) {
    if (element instanceof GrParameter) {
      conflicts.putValue(element, GroovyRefactoringBundle.message("variable.conflicts.with.parameter.0", CommonRefactoringUtil.htmlEmphasize(varName)));
    } else if (element instanceof GrField) {
      conflicts.putValue(element, GroovyRefactoringBundle.message("variable.conflicts.with.field.0", CommonRefactoringUtil.htmlEmphasize(varName)));
    } else {
      conflicts.putValue(element, GroovyRefactoringBundle.message("variable.conflicts.with.variable.0", CommonRefactoringUtil.htmlEmphasize(varName)));
    }
  }

  public static class ParameterNameSuggester {
    private final String myName;
    private final GrParameter myParameter;

    public ParameterNameSuggester(String name, GrParameter parameter) {
      myName = name;
      myParameter = parameter;
    }

    public String generateName() {
      String name = myName;
      int i = 1;
      MultiMap<PsiElement, String> confl = new MultiMap<>();
      while (!validateNewParameterName(myParameter, confl, name)) {
        name = myName + i;
        i++;
        confl = new MultiMap<>();
      }
      return name;
    }
  }


}
