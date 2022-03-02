// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CascadeController implements LinearGraphController {
  private final @NotNull LinearGraphController myDelegateController;
  @NotNull protected final PermanentGraphInfo<?> myPermanentGraphInfo;

  protected CascadeController(@NotNull LinearGraphController delegateController, @NotNull PermanentGraphInfo<?> permanentGraphInfo) {
    myDelegateController = delegateController;
    myPermanentGraphInfo = permanentGraphInfo;
  }

  @NotNull
  @Override
  public LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    LinearGraphAnswer answer = performAction(action);
    if (answer != null) {
      return answer;
    }
    VisibleGraphImpl.LinearGraphActionImpl delegateAction =
      new VisibleGraphImpl.LinearGraphActionImpl(convertToDelegate(action.getAffectedElement()), action.getType());
    return delegateGraphChanged(myDelegateController.performLinearGraphAction(delegateAction));
  }

  @Nullable
  GraphChanges<Integer> performAction(@NotNull Function<? super CascadeController, ? extends GraphChanges<Integer>> action) {
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
    return myDelegateController;
  }

  @NotNull
  public PermanentGraphInfo<?> getPermanentGraphInfo() {
    return myPermanentGraphInfo;
  }

  @NotNull
  protected abstract LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer);

  // null mean that this action must be performed by delegateGraphController
  @Nullable
  protected abstract LinearGraphAnswer performAction(@NotNull LinearGraphAction action);
}
