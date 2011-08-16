/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * set priority to current class members
 *
 * @author Maxim.Medvedev
 */
public class GrTopClassMembersWeigher extends CompletionWeigher {
  @Override
  public Integer weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }
    if (!(o instanceof PsiMember) || !(location.getCompletionParameters().getPosition().getContainingFile() instanceof GroovyFileBase)) return null;

    final PsiElement position = location.getCompletionParameters().getPosition();

    final PsiElement parent = position.getParent();
    if (!(parent instanceof GrReferenceExpression)) return 0;

    final GrExpression qualifier = ((GrReferenceExpression)parent).getQualifierExpression();
    final PsiClass psiClass;
    if (qualifier == null) {
      psiClass = PsiUtil.getContextClass(position);
    }
    else {
      final PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) return 0;

      psiClass = ((PsiClassType)type).resolve();
    }
    if (psiClass == null) return 0;

    if (PsiManager.getInstance(location.getProject()).areElementsEquivalent(((PsiMember)o).getContainingClass(), psiClass)) {
      return 1;
    }
    return 0;
  }
}
