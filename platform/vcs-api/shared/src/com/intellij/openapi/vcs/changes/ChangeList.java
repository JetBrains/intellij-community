// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface ChangeList {
  @Unmodifiable
  Collection<Change> getChanges();

  @NlsSafe
  @NotNull
  String getName();

  @NlsSafe
  String getComment();
}
