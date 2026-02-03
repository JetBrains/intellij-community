// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;

public abstract class BuilderMethodsContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     @Nullable PsiClass clazz,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    if (clazz == null) return;

    String name = ResolveUtil.getNameHint(processor);
    if (name == null) return;

    if (!ResolveUtilKt.shouldProcessDynamicMethods(processor)) return;

    processDynamicMethods(qualifierType, clazz, name, place, e -> processor.execute(e, state));
  }

  abstract boolean processDynamicMethods(@NotNull PsiType qualifierType,
                                         @NotNull PsiClass clazz,
                                         @NotNull String name,
                                         @NotNull PsiElement place,
                                         @NotNull Processor<? super PsiElement> processor);
}
