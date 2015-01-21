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

import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CascadeLinearGraphController implements LinearGraphController {
  @Nullable
  private final CascadeLinearGraphController myDelegateLinearGraphController;
  @NotNull
  protected final PermanentGraphInfo myPermanentGraphInfo;

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
    }
    if (answer != null)
      return answer;
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }

  @NotNull
  protected CascadeLinearGraphController getDelegateLinearGraphController() {
    assert myDelegateLinearGraphController != null;
    return myDelegateLinearGraphController;
  }

  @NotNull
  protected abstract LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer);

  // null mean that this action must be performed by delegateGraphController
  @Nullable
  protected abstract LinearGraphAnswer performAction(@NotNull LinearGraphAction action);

}
