// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * {@link RefGroup} containing only one {@link VcsRef}.
 */
@ApiStatus.Internal
public final class SingletonRefGroup implements RefGroup {
  private final @NotNull VcsRef myRef;

  public SingletonRefGroup(@NotNull VcsRef ref) {
    myRef = ref;
  }

  @Override
  public boolean isExpanded() {
    return false;
  }

  @Override
  public @NotNull String getName() {
    return myRef.getName();
  }

  @Override
  public @NotNull List<VcsRef> getRefs() {
    return Collections.singletonList(myRef);
  }

  @Override
  public @NotNull List<Color> getColors() {
    return Collections.singletonList(myRef.getType().getBackgroundColor());
  }

  public @NotNull VcsRef getRef() {
    return myRef;
  }
}
