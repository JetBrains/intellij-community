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

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CascadeController implements LinearGraphController {
  @Nullable private final LinearGraphController myDelegateController;
  @NotNull protected final PermanentGraphInfo myPermanentGraphInfo;

  protected CascadeController(@Nullable LinearGraphController delegateController, @NotNull PermanentGraphInfo permanentGraphInfo) {
    myDelegateController = delegateController;
    myPermanentGraphInfo = permanentGraphInfo;
  }

  @NotNull
  @Override
  public LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    LinearGraphAnswer answer = performAction(action);
    if (answer == null && myDelegateController != null) {
      answer = myDelegateController.performLinearGraphAction(
        new VisibleGraphImpl.LinearGraphActionImpl(convertToDelegate(action.getAffectedElement()), action.getType()));
      answer = delegateGraphChanged(answer);
    }
    if (answer != null) return answer;
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  @Nullable
  GraphChanges<Integer> performAction(@NotNull Function<CascadeController, GraphChanges<Integer>> action) {
    GraphChanges<Integer> graphChanges = action.fun(this);
    if (graphChanges != null) return graphChanges;

    if (myDelegateController instanceof CascadeController) {
      GraphChanges<Integer> result = ((CascadeController)myDelegateController).performAction(action);
      if (result != null) {
        return delegateGraphChanged(new LinearGraphController.LinearGraphAnswer(result)).getGraphChanges();
      }
    }
    return null;
  }

  @Nullable
  private PrintElementWithGraphElement convertToDelegate(@Nullable PrintElementWithGraphElement element) {
    if (element == null) return null;
    GraphElement convertedGraphElement = convertToDelegate(element.getGraphElement());
    if (convertedGraphElement == null) return null;
    return PrintElementWithGraphElement.converted(element, convertedGraphElement);
  }

  @Nullable
  protected GraphElement convertToDelegate(@NotNull GraphElement graphElement) {
    return graphElement;
  }

  @NotNull
  protected LinearGraphController getDelegateController() {
    assert myDelegateController != null;
    return myDelegateController;
  }

  @NotNull
  public PermanentGraphInfo getPermanentGraphInfo() {
    return myPermanentGraphInfo;
  }

  @NotNull
  protected abstract LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer);

  // null mean that this action must be performed by delegateGraphController
  @Nullable
  protected abstract LinearGraphAnswer performAction(@NotNull LinearGraphAction action);
}
