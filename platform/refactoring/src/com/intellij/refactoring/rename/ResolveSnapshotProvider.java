// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;

public abstract class ResolveSnapshotProvider {
  public abstract ResolveSnapshot createSnapshot(PsiElement scope);

  public abstract static class ResolveSnapshot {
    public abstract void apply(String name);
  }
}
