// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * checks the following case:
 * <p/>
 * def foo(@DelegatesTo.Target def targetArg, @DelegatesTo Closure cl) {}
 *
 * @author Max Medvedev
 */

public class DelegatesToAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (!GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO.equals(annotation.getQualifiedName())) return false;

    final PsiAnnotationMemberValue valueAttribute = annotation.findAttributeValue("value");

    if (valueAttribute == null) {
      final PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof GrModifierList) {
        final PsiElement parent1 = ((GrModifierList)owner).getParent();
        if (parent1 instanceof GrParameter) {
          final PsiElement parent = parent1.getParent();
          if (parent instanceof GrParameterList) {
            for (GrParameter parameter : ((GrParameterList)parent).getParameters()) {
              if (parameter.getModifierList().hasAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET)) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }
}
