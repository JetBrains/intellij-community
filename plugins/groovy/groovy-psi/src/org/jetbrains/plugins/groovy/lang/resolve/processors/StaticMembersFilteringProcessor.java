// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticMembersFilteringProcessor extends GrDelegatingScopeProcessorWithHints {

  public StaticMembersFilteringProcessor(@NotNull PsiScopeProcessor delegate, @Nullable String name) {
    super(delegate, name, null);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof PsiMember)) return true;
    if (!((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) return true;
    return super.execute(element, state);
  }
}
