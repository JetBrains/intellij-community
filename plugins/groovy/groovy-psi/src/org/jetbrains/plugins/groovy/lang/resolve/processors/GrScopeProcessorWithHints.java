// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class GrScopeProcessorWithHints implements PsiScopeProcessor, NameHint, ElementClassHint {
  protected final @Nullable Set<DeclarationKind> myResolveTargetKinds;
  protected final @Nullable String myName;

  public GrScopeProcessorWithHints(@Nullable String name,
                                   @Nullable Set<DeclarationKind> resolveTargets) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (NameHint.KEY == hintKey && myName != null) {
      return (T)this;
    }

    if (ElementClassHint.KEY == hintKey && myResolveTargetKinds != null) {
      return (T)this;
    }

    return null;
  }

  @Override
  public boolean shouldProcess(@NotNull DeclarationKind kind) {
    assert myResolveTargetKinds != null : "don't invoke shouldProcess if resolveTargets are not declared";
    return myResolveTargetKinds.contains(kind);
  }

  @Override
  public @NotNull String getName(@NotNull ResolveState state) {
    assert myName != null : "don't invoke getName if myName is not declared";
    return myName;
  }
}
