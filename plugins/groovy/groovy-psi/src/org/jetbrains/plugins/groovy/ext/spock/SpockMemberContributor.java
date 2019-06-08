// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SpockMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (ResolveUtil.shouldProcessProperties(classHint)) {
      GrMethod method = PsiTreeUtil.getParentOfType(place, GrMethod.class);
      if (method == null) return;

      if (aClass != method.getContainingClass()) return;

      Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

      String nameHint = ResolveUtil.getNameHint(processor);
      if (nameHint == null) {
        for (SpockVariableDescriptor spockVar : cachedValue.values()) {
          if (!processor.execute(spockVar.getVariable(), state)) return;
        }
      }
      else {
        SpockVariableDescriptor spockVar = cachedValue.get(nameHint);
        if (spockVar != null && spockVar.getNavigationElement() != place) {
          processor.execute(spockVar.getVariable(), state);
        }
      }
    }
  }

  @Override
  public String getParentClassName() {
    return SpockUtils.SPEC_CLASS_NAME;
  }
}
