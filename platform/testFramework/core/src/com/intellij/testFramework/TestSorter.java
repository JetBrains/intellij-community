// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.ToIntFunction;

public interface TestSorter {
  @NotNull
  List<Class> sorted(@NotNull List<Class> tests, @NotNull ToIntFunction<? super Class> ranker);
}
