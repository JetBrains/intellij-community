/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.MembersGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
class GroovyMembersGetter extends MembersGetter {
  private final PsiClassType myExpectedType;

  GroovyMembersGetter(PsiClassType expectedType, CompletionParameters parameters) {
    super(GrMainCompletionProvider.completeStaticMembers(parameters), parameters.getPosition());
    myExpectedType = JavaCompletionUtil.originalize(expectedType);
  }

  public void processMembers(boolean searchInheritors, final Consumer<LookupElement> results) {
    processMembers(results, myExpectedType.resolve(), PsiTreeUtil.getParentOfType(myPlace, GrAnnotation.class) == null, searchInheritors);
  }

  @Override
  protected LookupElement createFieldElement(PsiField field) {
    if (!isSuitableType(field.getType())) {
      return null;
    }

    return GrMainCompletionProvider.createGlobalMemberElement(field, field.getContainingClass(), false);
  }

  @Override
  protected LookupElement createMethodElement(PsiMethod method) {
    PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, myExpectedType);
    PsiType type = substitutor.substitute(method.getReturnType());
    if (!isSuitableType(type)) {
      return null;
    }

    return GrMainCompletionProvider.createGlobalMemberElement(method, method.getContainingClass(), false);
  }

  private boolean isSuitableType(PsiType type) {
    return type != null && TypesUtil.isAssignableByMethodCallConversion(myExpectedType, type, myPlace);
  }
}
