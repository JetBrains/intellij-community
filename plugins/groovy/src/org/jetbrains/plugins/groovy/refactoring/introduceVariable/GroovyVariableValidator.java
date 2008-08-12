/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyVariableValidator implements GroovyIntroduceVariableBase.Validator {
  private GroovyIntroduceVariableBase myIntroduceVariableBase;
  private Project myProject;
  private GrExpression myExpression;
  private GroovyPsiElement myContainer;
  private PsiElement[] myOccurrences;

  public boolean isOK(GroovyIntroduceVariableDialog dialog) {
    String varName = dialog.getEnteredName();
    boolean allOccurrences = dialog.isReplaceAllOccurrences();
    final ArrayList<String> conflicts = isOKImpl(varName, allOccurrences);
    return conflicts.size() <= 0 || myIntroduceVariableBase.reportConflicts(conflicts, getProject());
  }

  private ArrayList<String> isOKImpl(String varName, boolean replaceAllOccurrences) {
    if (replaceAllOccurrences) {
      GroovyRefactoringUtil.sortOccurrences(myOccurrences);
      assert myOccurrences != null && myOccurrences.length > 0 && myOccurrences[0] instanceof GrExpression;
      myExpression = ((GrExpression) myOccurrences[0]);
    }
    final ArrayList<String> conflicts = new ArrayList<String>();
    assert varName != null;
    validateVariableOccurrencesDown(myContainer, conflicts, varName);
    if (!(myContainer instanceof GroovyFileBase)) {
      validateVariableOccurrencesUp(myContainer, conflicts, varName);
    }
    return conflicts;
  }

  /**
   * Use for validator tests
   */
  public String isOKTest(String varName, boolean allOccurences) {
    ArrayList<String> list = isOKImpl(varName, allOccurences);
    String result = "";
    for (String s : list) {
      result = result + s.replaceAll("<b><code>", "").replaceAll("</code></b>", "") + "\n";
    }
    if (list.size() > 0) {
      result = result.substring(0, result.length() - 1);
    }
    if (result.length() == 0) {
      result = "ok";
    }
    return result;
  }


  public GroovyVariableValidator(
      final GroovyIntroduceVariableBase introduceVariableBase,
      Project project,
      GrExpression selectedExpr,
      PsiElement[] occurrences,
      GroovyPsiElement enclosingContainer) {
    myIntroduceVariableBase = introduceVariableBase;
    myOccurrences = occurrences;
    myProject = project;
    myExpression = selectedExpr;
    myContainer = enclosingContainer;
  }

  /**
   * @param startElement Container to start checking conflicts from
   * @param conflicts    Conflict accumulator
   * @param varName      Variable name
   */
  private void validateVariableOccurrencesDown(PsiElement startElement, ArrayList<String> conflicts, @NotNull String varName) {
    PsiElement child = startElement.getFirstChild();
    while (child != null) {
      // Do not check defined classes, methods, closures and blocks before
      if (child instanceof GrTypeDefinition ||
          child instanceof GrMethod ||
          GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(child) &&
              child.getTextRange().getEndOffset() < myExpression.getTextRange().getStartOffset()) {
        child = child.getNextSibling();
        continue;
      }
      if (child instanceof GrVariable) {
        if (child instanceof GrField) return;
        if (child instanceof GrParameter &&
            varName.equals(((GrParameter) child).getName())) {
          conflicts.add(GroovyRefactoringBundle.message("introduced.variable.conflicts.with.parameter.0",
              CommonRefactoringUtil.htmlEmphasize(varName)));
          return;
        } else if (varName.equals(((GrVariable) child).getName())) {
          conflicts.add(GroovyRefactoringBundle.message("introduced.variable.conflicts.with.variable.0",
              CommonRefactoringUtil.htmlEmphasize(varName)));
          return;
        } else {
          validateVariableOccurrencesDown(child, conflicts, varName);
        }
      } else {
        validateVariableOccurrencesDown(child, conflicts, varName);
      }
      child = child.getNextSibling();
    }
  }

  private void validateVariableOccurrencesUp(PsiElement startElement, ArrayList<String> conflicts, @NotNull String varName) {
    PsiElement prevSibling = startElement.getPrevSibling();
    while (prevSibling != null) {
      validateVariableOccurrencesDown(prevSibling, conflicts, varName);
      prevSibling = prevSibling.getPrevSibling();
    }

    PsiElement parent = startElement.getParent();
    // Do not check context out of method, type definition and directories
    if (parent == null ||
        parent instanceof GrMethod ||
        parent instanceof GrTypeDefinition ||
        parent instanceof GroovyFileBase ||
        parent instanceof PsiDirectory) return;

    validateVariableOccurrencesUp(parent, conflicts, varName);
  }

  /**
   * Validates name to be suggested in context
   */
  public String validateName(String name, boolean increaseNumber) {
    String result = name;
    if (!isOKTest(name, true).equals("ok") && !increaseNumber ||
        name.length() == 0) {
      return "";
    }
    int i = 1;
    while (!isOKTest(result, true).equals("ok")) {
      result = name + i;
      i++;
    }
    return result;
  }

  public Project getProject() {
    return myProject;
  }
}
