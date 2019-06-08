// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (aClass == null) return;
    PsiClass pageClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("geb.Page", place.getResolveScope());

    if (pageClass != null) {
      pageClass.processDeclarations(processor, state, null, place);
    }
  }
}
