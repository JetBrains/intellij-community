/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.scope.NameHint;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessor {
  private GroovyResolveResult myProperty = null;

  public PropertyResolverProcessor(String name, PsiElement place, boolean forCompletion) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, forCompletion, PsiType.EMPTY_ARRAY);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (myName != null && element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      boolean lValue = myPlace instanceof GroovyPsiElement && PsiUtil.isLValue((GroovyPsiElement) myPlace);
      if (!lValue && PsiUtil.isSimplePropertyGetter(method, myName)) {
        myCandidates.clear();
        super.execute(element, substitutor);
        return false;
      } else if (lValue && PsiUtil.isSimplePropertySetter(method, myName)) {
        myCandidates.clear();
        super.execute(element, substitutor);
        return false;
      }
    } else if (myName == null || myName.equals(((PsiNamedElement) element).getName())) {
      if (!myForCompletion &&
          element instanceof GrField && ((GrField) element).isProperty()) {
        if (myProperty == null) {
          boolean isAccessible = isAccessible((PsiNamedElement) element);
          boolean isStaticsOK = isStaticsOK((PsiNamedElement) element);
          myProperty = new GroovyResolveResultImpl(element, myCurrentFileResolveContext, substitutor, isAccessible, isStaticsOK);
        }
        return true;
      }
      return super.execute(element, substitutor);
    }

    return true;
  }

  public GroovyResolveResult[] getCandidates() {
    if (myProperty != null) {
      if (myForCompletion) {
        myCandidates.add(myProperty); //could be completed to avoid runtime errors in some cases - see e.g. contr4.test
      } else {
        boolean containingAccessorFound = false;
        for (Iterator<GroovyResolveResult> it = myCandidates.iterator(); it.hasNext();) {
          PsiElement element = it.next().getElement();
          if (element instanceof GrMethod && PsiTreeUtil.isAncestor(element, myPlace, false)) {
            it.remove();
            containingAccessorFound = true;
            break;
          }
        }

        if (containingAccessorFound) {
          myCandidates.add(myProperty);
        }
      }

      myProperty = null;
    }

    return super.getCandidates();
  }

  public <T> T getHint(Class<T> hintClass) {
    if (NameHint.class == hintClass) {
      //we cannot provide name hint here
      return null;
    }

    return super.getHint(hintClass);
  }

}
