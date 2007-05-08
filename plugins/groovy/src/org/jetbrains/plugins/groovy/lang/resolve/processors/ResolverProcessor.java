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
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;

import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
public class ResolverProcessor implements PsiScopeProcessor, NameHint, ClassHint {
  private String myName;
  private EnumSet<ResolveKind> myResolveTargetKinds;

  private List<PsiNamedElement> myCandidates = new ArrayList<PsiNamedElement>();

  public ResolverProcessor(String name, EnumSet<ResolveKind> resolveTargets) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (myResolveTargetKinds.contains(ResolveUtil.getResolveKind(element))) {
      PsiNamedElement namedElement = (PsiNamedElement) element;
      myCandidates.add(namedElement);
    }

    return myName == null || myCandidates.size() == 0;
  }

  public List<PsiNamedElement> getCandidates() {
    return myCandidates;
  }

  public <T> T getHint(Class<T> hintClass) {
    if (NameHint.class == hintClass && myName != null){
      return (T) this;
    }
    else if (ClassHint.class == hintClass) {
      return (T) this;
    }

    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  public String getName() {
    return myName;
  }

  public boolean shouldProcess(ResolveKind resolveKind) {
    return myResolveTargetKinds.contains(resolveKind);
  }
}
