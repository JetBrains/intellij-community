/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

/**
 * @author Sergey Evdokimov
 */
public class GebBrowserMemberContributor extends NonCodeMembersContributor {

  @Override
  protected String getParentClassName() {
    return "geb.Browser";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     @NotNull ResolveState state) {
    PsiClass pageClass = GroovyPsiManager.getInstance(aClass.getProject()).findClassWithCache("geb.Page", place.getResolveScope());

    if (pageClass != null) {
      if (!pageClass.processDeclarations(processor, state, null, place)) return;
    }
  }

}
