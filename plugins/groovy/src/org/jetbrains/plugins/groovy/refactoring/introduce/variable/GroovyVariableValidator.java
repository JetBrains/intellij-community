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

package org.jetbrains.plugins.groovy.refactoring.introduce.variable;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.ConflictReporter;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceValidatorEngine;

/**
 * @author ilyas
 */
public class GroovyVariableValidator extends GrIntroduceValidatorEngine implements GrIntroduceVariableHandler.Validator {
  public GroovyVariableValidator(GrIntroduceContext context) {
    super(context, new ConflictReporter() {
      @Override
      public void check(PsiElement element, MultiMap<PsiElement, String> conflicts, String varName) {
        if (!(element instanceof GrVariable)) return;
        final GrVariable var = (GrVariable)element;

        if (var instanceof GrField) return;

        if (var instanceof GrParameter && varName.equals(var.getName())) {
          conflicts.putValue(var, GroovyRefactoringBundle
            .message("introduced.variable.conflicts.with.parameter.0", CommonRefactoringUtil.htmlEmphasize(varName)));
        }
        else if (varName.equals(var.getName())) {
          conflicts.putValue(var, GroovyRefactoringBundle
            .message("introduced.variable.conflicts.with.variable.0", CommonRefactoringUtil.htmlEmphasize(varName)));
        }
      }
    });
  }
}
