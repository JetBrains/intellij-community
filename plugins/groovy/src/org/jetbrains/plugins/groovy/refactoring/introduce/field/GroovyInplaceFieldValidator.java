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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.ConflictReporter;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceValidatorEngine;

/**
 * @author Max Medvedev
 */
public class GroovyInplaceFieldValidator extends GrIntroduceValidatorEngine {
  public GroovyInplaceFieldValidator(GrIntroduceContext context) {
    super(context, new ConflictReporter() {
      @Override
      public void check(PsiElement toCheck, MultiMap<PsiElement, String> conflicts, String varName) {
        if (toCheck instanceof GrVariable && varName.equals(((GrVariable)toCheck).getName())) {
          conflicts.putValue(toCheck, GroovyRefactoringBundle.message("field.0.is.already.defined", CommonRefactoringUtil.htmlEmphasize(varName)));
        }
        if (toCheck instanceof GrMethod) {
          if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)toCheck) &&
              varName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(((PsiMethod)toCheck).getName()))) {
            conflicts.putValue(toCheck, GroovyRefactoringBundle
              .message("access.to.created.field.0.will.be.overriden.by.method.1", CommonRefactoringUtil.htmlEmphasize(varName),
                                                CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(toCheck))));
          }
        }
      }
    });
  }
}
