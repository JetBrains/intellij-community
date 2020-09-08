// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ChangeList {
  Collection<Change> getChanges();

  @NlsSafe
  @NotNull
  String getName();

  @NlsSafe
  String getComment();
}
