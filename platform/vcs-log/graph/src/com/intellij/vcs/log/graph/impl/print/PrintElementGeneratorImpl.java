/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.impl.print;

import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.PrintedLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator;
import com.intellij.vcs.log.graph.api.printer.SelectedPrintElementsController;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PrintElementGeneratorImpl implements PrintElementGenerator {

  @NotNull
  private final PrintedLinearGraph myPrintedLinearGraph;

  @NotNull
  private final EdgesInRowGenerator myEdgesInRowGenerator;

  @NotNull
  private final SelectedPrintElementsController mySelectedElementsController;

  public PrintElementGeneratorImpl(@NotNull PrintedLinearGraph printedLinearGraph,
                                   @NotNull SelectedPrintElementsController selectedElementsController) {
    myPrintedLinearGraph = printedLinearGraph;
    mySelectedElementsController = selectedElementsController;
    myEdgesInRowGenerator = new EdgesInRowGenerator(printedLinearGraph);
  }

  @NotNull
  @Override
  public Collection<PrintElement> getPrintElements(int visibleRow) {
    return null;
  }

  @NotNull
  @Override
  public GraphElement getRelatedGraphElement(@NotNull PrintElement printElement) {
    return null;
  }

  @Override
  public boolean areLongEdgesHidden() {
    return false;
  }

  @Override
  public void setLongEdgesHidden(boolean longEdgesHidden) {

  }
}
