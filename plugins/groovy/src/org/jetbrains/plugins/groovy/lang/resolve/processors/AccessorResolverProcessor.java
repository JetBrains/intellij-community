/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Maxim.Medvedev
 */
public class AccessorResolverProcessor extends ResolverProcessor {
  private final boolean mySearchForGetter;
  private final SubstitutorComputer mySubstitutorComputer;

  public AccessorResolverProcessor(String name, GroovyPsiElement place, boolean searchForGetter) {
    this(name, place, searchForGetter, false, null, PsiType.EMPTY_ARRAY);
  }

  public AccessorResolverProcessor(String name,
                                   GroovyPsiElement place,
                                   boolean searchForGetter,
                                   boolean byShape,
                                   @Nullable PsiType thisType,
                                   @NotNull PsiType[] typeArguments) {
    super(name, RESOLVE_KINDS_METHOD, place, PsiType.EMPTY_ARRAY);
    mySearchForGetter = searchForGetter;
    mySubstitutorComputer = byShape ? null : new SubstitutorComputer(thisType, PsiType.EMPTY_ARRAY, typeArguments, false, place) {
      @Override
      protected PsiElement getPlaceToInferContext() {
        return myPlace;
      }
    };
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (mySearchForGetter) {
      if (element instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)element, null)) {
        return addAccessor((PsiMethod)element, state);
      }
    }
    else {
      if (element instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)element, null)) {
        return addAccessor((PsiMethod)element, state);
      }
    }
    return true;
  }

  private boolean addAccessor(PsiMethod method, ResolveState state) {
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

    if (mySubstitutorComputer != null) {
      substitutor = mySubstitutorComputer.obtainSubstitutor(substitutor, method, state);
    }
    boolean isAccessible = isAccessible(method);
    final GroovyPsiElement resolveContext = state.get(RESOLVE_CONTEXT);
    boolean isStaticsOK = isStaticsOK(method, resolveContext, true);
    addCandidate(new GroovyResolveResultImpl(method, resolveContext, substitutor, isAccessible, isStaticsOK, true));
    return !isAccessible || !isStaticsOK;
  }
}
