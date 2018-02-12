/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * Only methods without arguments could be annotated with @Delegate
 */
public class DelegateAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (!GroovyCommonClassNames.GROOVY_LANG_DELEGATE.equals(annotation.getQualifiedName())) return false;
    PsiElement annoParent = annotation.getParent();
    PsiElement owner = annoParent instanceof PsiModifierList ? annoParent.getParent() : annoParent;
    if (!(owner instanceof GrMethod)) {
      return false;
    }
    if (((GrMethod)owner).getParameterList().getParametersCount() > 0) {
      holder.createErrorAnnotation(annotation, GroovyBundle.message("delegate.annotation.is.only.for.methods.without.arguments"));
      return true;
    }

    return false;
  }
}
