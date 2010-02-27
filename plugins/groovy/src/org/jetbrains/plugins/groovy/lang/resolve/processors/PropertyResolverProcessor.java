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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.EnumSet;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessor {
  private GroovyResolveResult myProperty = null;

  public PropertyResolverProcessor(String name, PsiElement place) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, PsiType.EMPTY_ARRAY);
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myName != null && element instanceof PsiMethod && !(element instanceof GrAccessorMethod)) {
      PsiMethod method = (PsiMethod)element;
      boolean lValue = myPlace instanceof GroovyPsiElement && PsiUtil.isLValue((GroovyPsiElement)myPlace);
      if (!lValue && GroovyPropertyUtils.isSimplePropertyGetter(method, myName) ||
          lValue && GroovyPropertyUtils.isSimplePropertySetter(method, myName)) {
        if (method instanceof GrMethod && isFieldReferenceInSameClass(method, myName)) {
          return true;
        }

        myCandidates.clear();
        super.execute(element, state);
        return false;
      }
    }
    else if (myName == null || myName.equals(((PsiNamedElement)element).getName())) {
      if (element instanceof GrField && ((GrField)element).isProperty()) {
        if (myProperty == null) {
          PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
          substitutor = substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
          myProperty =
            new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible((PsiNamedElement)element),
                                        isStaticsOK((PsiNamedElement)element));
        }
        return true;
      }
      else if (element instanceof GrReferenceExpression) {
        if (((GrReferenceExpression)element).getQualifier() != null) {
          return true;
        }
      }
      return super.execute(element, state);
    }

    return true;
  }

  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (myProperty != null) {
      if (myCandidates.isEmpty()) {
        myCandidates.add(myProperty);
      }

      myProperty = null;
    }

    return super.getCandidates();
  }

  public <T> T getHint(Key<T> hintKey) {
    if (NameHint.KEY == hintKey) {
      //we cannot provide name hint here
      return null;
    }

    return super.getHint(hintKey);
  }

}
