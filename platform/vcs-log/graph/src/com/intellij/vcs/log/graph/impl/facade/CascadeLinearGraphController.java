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
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementManager;
import com.intellij.vcs.log.graph.impl.print.ColorGetterByLayoutIndex;
import com.intellij.vcs.log.graph.impl.print.GraphElementComparatorByLayoutIndex;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public abstract class CascadeLinearGraphController implements LinearGraphController {
  @Nullable
  private final CascadeLinearGraphController myDelegateLinearGraphController;
  @NotNull
  protected final PermanentGraphInfo myPermanentGraphInfo;
  @Nullable
  private PrintElementManagerImpl myPrintElementManager;

  protected CascadeLinearGraphController(@Nullable CascadeLinearGraphController delegateLinearGraphController,
                                         @NotNull PermanentGraphInfo permanentGraphInfo) {
    myDelegateLinearGraphController = delegateLinearGraphController;
    myPermanentGraphInfo = permanentGraphInfo;
  }

  @NotNull
  @Override
  public LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    LinearGraphAnswer answer = performAction(action);
    if (answer == null && myDelegateLinearGraphController != null) {
      answer = myDelegateLinearGraphController.performLinearGraphAction(action);
      answer = performDelegateUpdate(answer);
      myPrintElementManager = createPrintElementManager(myPermanentGraphInfo);
    }
    if (answer != null)
      return answer;
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  @NotNull
  @Override
  public PrintElementManager getPrintElementManager() {
    if (myPrintElementManager == null) {
      myPrintElementManager = createPrintElementManager(myPermanentGraphInfo);
    }
    return myPrintElementManager;
  }

  @NotNull
  protected CascadeLinearGraphController getDelegateLinearGraphController() {
    assert myDelegateLinearGraphController != null;
    return myDelegateLinearGraphController;
  }

  protected abstract boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement);

  @NotNull
  protected abstract LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer);

  // null mean that this action must be performed by delegateGraphController
  @Nullable
  protected abstract LinearGraphAnswer performAction(@NotNull LinearGraphAction action);

  private <CommitId> PrintElementManagerImpl createPrintElementManager(final PermanentGraphInfo<CommitId> permanentGraphInfo) {
    Comparator<GraphElement> graphElementComparator = new GraphElementComparatorByLayoutIndex(new NotNullFunction<Integer, Integer>() {
      @NotNull
      @Override
      public Integer fun(Integer nodeIndex) {
        int nodeId = getCompiledGraph().getNodeId(nodeIndex);
        if (nodeId < 0)
          return nodeId;
        return permanentGraphInfo.getPermanentGraphLayout().getLayoutIndex(nodeId);
      }
    });
    ColorGetterByLayoutIndex<CommitId> colorGetter = new ColorGetterByLayoutIndex<CommitId>(getCompiledGraph(), permanentGraphInfo);
    return new PrintElementManagerImpl(graphElementComparator, colorGetter);
  }

  private class PrintElementManagerImpl implements PrintElementManager {
    @NotNull
    private final Comparator<GraphElement> myGraphElementComparator;
    @NotNull
    private final ColorGetterByLayoutIndex myColorGetter;

    private PrintElementManagerImpl(@NotNull Comparator<GraphElement> graphElementComparator, @NotNull ColorGetterByLayoutIndex colorGetter) {
      myGraphElementComparator = graphElementComparator;
      myColorGetter = colorGetter;
    }

    @Override
    public boolean isSelected(@NotNull PrintElementWithGraphElement printElement) {
      return CascadeLinearGraphController.this.elementIsSelected(printElement);
    }

    @Override
    public int getColorId(@NotNull GraphElement element) {
      return myColorGetter.getColorId(element);
    }

    @NotNull
    @Override
    public Comparator<GraphElement> getGraphElementComparator() {
      return myGraphElementComparator;
    }
  }
}
