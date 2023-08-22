/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph.impl.print.elements;

import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager;
import org.jetbrains.annotations.NotNull;

public class TerminalEdgePrintElement extends EdgePrintElementImpl {
  public TerminalEdgePrintElement(int rowIndex,
                                  int position,
                                  @NotNull Type type,
                                  @NotNull GraphEdge graphEdge,
                                  @NotNull PrintElementPresentationManager presentationManager) {
    super(rowIndex, position, position, type, graphEdge, true, presentationManager);
  }
}
