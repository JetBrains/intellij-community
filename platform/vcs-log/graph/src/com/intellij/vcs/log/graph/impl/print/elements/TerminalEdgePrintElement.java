// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print.elements;

import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class TerminalEdgePrintElement extends EdgePrintElementImpl {
  public TerminalEdgePrintElement(int rowIndex,
                                  int position,
                                  @NotNull Type type,
                                  @NotNull GraphEdge graphEdge,
                                  @NotNull PrintElementPresentationManager presentationManager) {
    super(rowIndex, position, position, type, graphEdge, true, presentationManager);
  }
}
