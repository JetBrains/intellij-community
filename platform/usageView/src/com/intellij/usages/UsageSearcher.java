// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages;

import com.intellij.util.Generator;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface UsageSearcher extends Generator<Usage> {
  // hands all found usages to the processor
  // not guaranteed to be in read action, not guaranteed in the same thread
  @Override
  void generate(@NotNull Processor<? super Usage> processor);
}
