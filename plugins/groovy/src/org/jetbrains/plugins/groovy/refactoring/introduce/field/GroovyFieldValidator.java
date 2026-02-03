// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.ConflictReporter;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceValidatorEngine;

/**
 * @author Maxim.Medvedev
 */
public class GroovyFieldValidator extends GrIntroduceValidatorEngine {
  public GroovyFieldValidator(GrIntroduceContext context) {
    super(context, new ConflictReporter() {
      @Override
      public void check(PsiElement toCheck, MultiMap<PsiElement, String> conflicts, String varName) {
        if (toCheck instanceof GrField && varName.equals(((GrField)toCheck).getName())) {
          conflicts.putValue(toCheck, GroovyRefactoringBundle.message("field.0.is.already.defined", CommonRefactoringUtil.htmlEmphasize(varName)));
        }
        if (toCheck instanceof GrMethod) {
          if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)toCheck) &&
              varName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(((PsiMethod)toCheck).getName()))) {
            conflicts.putValue(toCheck, GroovyRefactoringBundle
              .message("access.to.created.field.0.will.be.overridden.by.method.1", CommonRefactoringUtil.htmlEmphasize(varName),
                       CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(toCheck))));
          }
        }
      }
    });
  }
}
