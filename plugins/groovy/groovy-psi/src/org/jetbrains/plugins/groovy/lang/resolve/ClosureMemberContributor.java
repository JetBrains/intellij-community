// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

/**
 * @author Sergey Evdokimov
 */
public abstract class ClosureMemberContributor extends NonCodeMembersContributor {

  @Override
  protected final @NotNull String getParentClassName() {
    return GROOVY_LANG_CLOSURE;
  }

  @Override
  public final void processDynamicElements(@NotNull PsiType qualifierType,
                                           @NotNull PsiScopeProcessor processor,
                                           @NotNull PsiElement place,
                                           @NotNull ResolveState state) {
    final PsiElement context = state.get(ClassHint.RESOLVE_CONTEXT);
    if (!(context instanceof GrClosableBlock)) return;

    processMembers((GrClosableBlock)context, processor, place, state);
  }

  protected abstract void processMembers(@NotNull GrClosableBlock closure,
                                         @NotNull PsiScopeProcessor processor,
                                         @NotNull PsiElement place,
                                         @NotNull ResolveState state);
}
