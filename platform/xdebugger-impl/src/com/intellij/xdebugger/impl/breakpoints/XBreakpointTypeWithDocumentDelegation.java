// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public interface XBreakpointTypeWithDocumentDelegation {
  /*
   * return a custom document which should be used for breakpoint highlighting
   */
  @NotNull
  Document getDocumentForHighlighting(@NotNull Document document);
}
