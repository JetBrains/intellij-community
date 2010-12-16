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

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceSettings;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author ilyas
 */
public class GroovyVariableValidator implements GroovyIntroduceVariableBase.Validator {
  private final GrIntroduceContext myContext;

  public boolean isOK(GrIntroduceDialog dialog) {
    final GrIntroduceSettings settings = dialog.getSettings();
    if (settings==null) return false;
    String varName = settings.getName();
    boolean allOccurrences = settings.replaceAllOccurrences();
    final MultiMap<PsiElement, String> conflicts = isOKImpl(varName, allOccurrences);
    return conflicts.size() <= 0 || reportConflicts(conflicts, getProject());
  }

  private static boolean reportConflicts(final MultiMap<PsiElement, String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  private MultiMap<PsiElement, String> isOKImpl(String varName, boolean replaceAllOccurrences) {
    GrExpression firstOccurence;
    if (replaceAllOccurrences) {
      GroovyRefactoringUtil.sortOccurrences(myContext.occurrences);
      firstOccurence = ((GrExpression)myContext.occurrences[0]);
    } else {
      firstOccurence = myContext.expression;
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    assert varName != null;
    validateVariableOccurrencesDown(myContext.scope, conflicts, varName, firstOccurence.getTextRange().getStartOffset());
    if (!(myContext.scope instanceof GroovyFileBase)) {
      validateVariableOccurrencesUp(myContext.scope, conflicts, varName, firstOccurence.getTextRange().getStartOffset());
    }
    return conflicts;
  }

  /**
   * Use for validator tests
   */
  public String isOKTest(String varName, boolean allOccurences) {
    MultiMap<PsiElement, String> list = isOKImpl(varName, allOccurences);
    String result = "";
    final String[] strings = ArrayUtil.toStringArray((Collection<String>)list.values());
    Arrays.sort(strings, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.compareTo(o2);
      }
    });

    for (String s : strings) {
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


  public GroovyVariableValidator(GrIntroduceContext context) {
    myContext =context;
  }

  /**
   * @param startElement Container to start checking conflicts from
   * @param conflicts    Conflict accumulator
   * @param varName      Variable name
   * @param startOffset
   */
  private static void validateVariableOccurrencesDown(PsiElement startElement,
                                                      MultiMap<PsiElement, String> conflicts,
                                                      @NotNull String varName,
                                                      double startOffset) {
    PsiElement child = startElement.getFirstChild();
    while (child != null) {
      // Do not check defined classes, methods, closures and blocks before
      if (child instanceof GrTypeDefinition ||
          child instanceof GrMethod ||
          GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(child) &&
          child.getTextRange().getEndOffset() < startOffset) {
        child = child.getNextSibling();
        continue;
      }
      if (child instanceof GrVariable) {
        if (child instanceof GrField) return;
        if (child instanceof GrParameter &&
            varName.equals(((GrParameter)child).getName())) {
          conflicts.putValue(child, GroovyRefactoringBundle
            .message("introduced.variable.conflicts.with.parameter.0", CommonRefactoringUtil.htmlEmphasize(varName)));
          return;
        }
        else if (varName.equals(((GrVariable)child).getName())) {
          conflicts.putValue(child, GroovyRefactoringBundle
            .message("introduced.variable.conflicts.with.variable.0", CommonRefactoringUtil.htmlEmphasize(varName)));
          return;
        }
        else {
          validateVariableOccurrencesDown(child, conflicts, varName, startOffset);
        }
      }
      else {
        validateVariableOccurrencesDown(child, conflicts, varName, startOffset);
      }
      child = child.getNextSibling();
    }
  }

  private static void validateVariableOccurrencesUp(PsiElement startElement,
                                                    MultiMap<PsiElement, String> conflicts,
                                                    @NotNull String varName,
                                                    double startOffset) {
    PsiElement prevSibling = startElement.getPrevSibling();
    while (prevSibling != null) {
      if (!(GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(prevSibling) &&
            prevSibling.getTextRange().getEndOffset() < startOffset)) {
        validateVariableOccurrencesDown(prevSibling, conflicts, varName, startOffset);
      }
      prevSibling = prevSibling.getPrevSibling();
    }

    PsiElement parent = startElement.getParent();
    // Do not check context out of method, type definition and directories
    if (parent == null ||
        parent instanceof GrMethod ||
        parent instanceof GrTypeDefinition ||
        parent instanceof GroovyFileBase ||
        parent instanceof PsiDirectory) {
      return;
    }

    validateVariableOccurrencesUp(parent, conflicts, varName, startOffset);
  }

  /**
   * Validates name to be suggested in context
   */
  public String validateName(String name, boolean increaseNumber) {
    String result = name;
    if (isOKImpl(name, true).size() > 0 && !increaseNumber || name.length() == 0) {
      return "";
    }
    int i = 1;
    while (isOKImpl(result, true).size() > 0) {
      result = name + i;
      i++;
    }
    return result;
  }

  public Project getProject() {
    return myContext.project;
  }
}
