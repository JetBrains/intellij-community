// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class GrDelegatingScopeProcessorWithHints extends GrScopeProcessorWithHints {
  private final PsiScopeProcessor myDelegate;

  public GrDelegatingScopeProcessorWithHints(@NotNull PsiScopeProcessor delegate,
                                             @NotNull DeclarationKind target,
                                             @NotNull DeclarationKind... targets) {
    this(delegate, EnumSet.of(target, targets));
  }

  public GrDelegatingScopeProcessorWithHints(@NotNull PsiScopeProcessor delegate, @Nullable Set<DeclarationKind> resolveTargets) {
    this(delegate, null, resolveTargets);
  }

  public GrDelegatingScopeProcessorWithHints(@NotNull PsiScopeProcessor delegate,
                                             @Nullable String name,
                                             @Nullable Set<DeclarationKind> resolveTargets) {
    super(name, resolveTargets);
    myDelegate = delegate;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return myDelegate.execute(element, state);
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
    myDelegate.handleEvent(event, associated);
  }

  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    T hint = super.getHint(hintKey);
    if (hint != null) return hint;

    return myDelegate.getHint(hintKey);
  }
}
