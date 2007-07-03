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

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.EnumSet;

import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.openapi.diagnostic.Logger;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessor {
  private static Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor");

  public PropertyResolverProcessor(String name, EnumSet<ResolveKind> resolveKinds, GroovyPsiElement place, boolean forCompletion) {
    super(name, resolveKinds, place, forCompletion, PsiType.EMPTY_ARRAY);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (myName == null || myName.equals(((PsiNamedElement) element).getName())) {
      return super.execute(element, substitutor);
    } else if (myName != null && element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      boolean lValue = PsiUtil.isLValue(myPlace);
      if (isSimplePropertyGetter(method) && !lValue) {
        String propName = PropertyUtil.getPropertyNameByGetter(method);
        if (myName.equals(propName)) {
          myCandidates.clear();
          super.execute(element, substitutor);
          return false;
        }
      } else if (isSimplePropertySetter(method) && lValue) {
        String propName = PropertyUtil.getPropertyNameBySetter(method);
        if (myName.equals(propName)) {
          myCandidates.clear();
          super.execute(element, substitutor);
          return false;
        }
      }
    }

    return true;
  }

  public <T> T getHint(Class<T> hintClass) {
    if (NameHint.class == hintClass) {
      //we cannot provide name hint here
      return null;
    }

    return super.getHint(hintClass);
  }

  //do not check return type
  private static boolean isSimplePropertyGetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();
    if (methodName.startsWith("get") && methodName.length() > "get".length()) {
      if (Character.isLowerCase(methodName.charAt("get".length()))
          && (methodName.length() == "get".length() + 1 || Character.isLowerCase(methodName.charAt("get".length() + 1)))) {
        return false;
      }
      return method.getParameterList().getParametersCount() == 0;
    }
    else if (methodName.startsWith("is")) {
      return method.getParameterList().getParametersCount() == 0;
    }
    return false;
  }

  private static boolean isSimplePropertySetter(PsiMethod method) {
    if (method == null) return false;

    if (method.isConstructor()) return false;

    String methodName = method.getName();

    if (!(methodName.startsWith("set") && methodName.length() > "set".length())) return false;
    if (Character.isLowerCase(methodName.charAt("set".length()))) return false;

    if (method.getParameterList().getParametersCount() != 1) {
      return false;
    }

    return true;
  }
}
