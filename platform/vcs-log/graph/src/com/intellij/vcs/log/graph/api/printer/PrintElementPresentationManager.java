// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.api.printer;

import com.intellij.vcs.log.graph.api.elements.GraphElement;
import org.jetbrains.annotations.NotNull;


public interface PrintElementPresentationManager {

  boolean isSelected(@NotNull GraphPrintElement printElement);

  int getColorId(@NotNull GraphElement element);
}
