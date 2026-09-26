// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.chains.DiffRequestChain;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ChainBackedDiffPreviewProvider extends DiffPreviewProvider {
  @Nullable
  DiffRequestChain createDiffRequestChain();
}
