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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ven
 * Resolves methods from method call or function application.
 */
public class MethodResolverProcessor extends ResolverProcessor {
  @Nullable
  PsiType[] myArgumentTypes;

  private List<GroovyResolveResult> myInapplicableCandidates = new ArrayList<GroovyResolveResult>();
  private List<PsiMethod> myCandidateMethods = new ArrayList<PsiMethod>();

  public MethodResolverProcessor(String name, GroovyPsiElement place, boolean forCompletion) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, forCompletion);
    myArgumentTypes = PsiUtil.getArgumentTypes(place);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor()) return true; //not interested in constructors <now>

      if (!isAccessible((PsiNamedElement) element)) return true;

      if (!myForCompletion) {
        if (ResolveUtil.isSuperMethodDominated(method, myCandidateMethods)) return true;
      }

      if (myForCompletion || PsiUtil.isApplicable(myArgumentTypes, method)) {
        myCandidates.add(new GroovyResolveResultImpl(method, true));
      }
      else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, true));
      }

      myCandidateMethods.add(method);
      return true;
    } else {
      return super.execute(element, substitutor);
    }
  }

  public GroovyResolveResult[] getCandidates() {
    return myCandidates.size() > 0 ? super.getCandidates() :
        myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
  }

  public boolean hasCandidates() {
    return super.hasCandidates() || myInapplicableCandidates.size() > 0;
  }
}
