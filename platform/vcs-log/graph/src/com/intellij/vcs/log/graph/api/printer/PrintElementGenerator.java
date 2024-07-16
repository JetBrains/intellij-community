// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.api.printer;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface PrintElementGenerator {
  @NotNull
  Collection<GraphPrintElement> getPrintElements(int visibleRow);
}
