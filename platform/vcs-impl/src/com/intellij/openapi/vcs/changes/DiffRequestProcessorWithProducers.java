// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.ListSelection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface DiffRequestProcessorWithProducers {
  @Nullable
  ListSelection<? extends DiffRequestProducer> collectDiffProducers(boolean selectedOnly);
}
