// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import static org.jetbrains.plugins.groovy.lang.psi.impl.FunctionalExpressionsKt.doGetOwnerType;
import static org.jetbrains.plugins.groovy.lang.psi.impl.FunctionalExpressionsKt.processOwnerAndDelegate;

public interface GrFunctionalExpression extends GrExpression, GrParameterListOwner {
  @NotNull
  GrParameter[] getAllParameters();

  default boolean processWithCallsiteDeclarations(@NotNull final PsiScopeProcessor plainProcessor,
                                                  @NotNull final ResolveState state,
                                                  @Nullable final PsiElement lastParent,
                                                  @NotNull final PsiElement place) {
    if (!processDeclarations(plainProcessor, state, lastParent, place)) return false;
    if (!processOwnerAndDelegate(this, plainProcessor, state, place)) return false;

    return true;
  }

  @Nullable
  default PsiType getOwnerType() {
    return CachedValuesManager
      .getCachedValue(this, () -> CachedValueProvider.Result.create(doGetOwnerType(this), PsiModificationTracker.MODIFICATION_COUNT));
  }
}
