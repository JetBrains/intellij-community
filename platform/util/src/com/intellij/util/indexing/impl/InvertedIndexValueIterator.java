// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntPredicate;

@ApiStatus.Internal
public interface InvertedIndexValueIterator<Value> extends ValueContainer.ValueIterator<Value> {
  @Override
  @NotNull IntPredicate getValueAssociationPredicate();

  Object getFileSetObject();
}
